package afg.achat.afgApprovAchat.service.perdiem;

import afg.achat.afgApprovAchat.model.perdiem.BaremePerDiem;
import afg.achat.afgApprovAchat.model.perdiem.BaremePerDiem.Statut;
import afg.achat.afgApprovAchat.model.perdiem.CategoriePersonnel;
import afg.achat.afgApprovAchat.model.perdiem.ZoneMission;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.perdiem.BaremePerDiemRepo;
import afg.achat.afgApprovAchat.repository.util.DeviseRepo;
import afg.achat.afgApprovAchat.service.securite.Habilitations;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Barème des per diem, en circuit maker / checker.
 *
 * <p>Le per diem est un forfait <b>par jour</b> de mission qui couvre les repas et les déplacements
 * locaux ; l'hébergement est payé directement par la banque. Une case du barème = une catégorie
 * de personnel × une zone de mission.</p>
 *
 * <ul>
 *   <li>Le RH MAKER <b>propose</b> un taux pour une case, à partir d'une date d'effet.
 *       Une seule proposition en attente par case.</li>
 *   <li>Le RH CHECKER (DRH) <b>valide</b> ou <b>rejette</b> ; il ne valide pas sa propre proposition
 *       (sauf administrateur). À la validation, le taux en vigueur de la même case s'arrête
 *       la veille de la nouvelle date d'effet ({@code dateFin} = date d'effet, exclue).</li>
 *   <li>L'auteur d'une proposition peut l'<b>annuler</b> tant qu'elle est en attente.</li>
 * </ul>
 *
 * <p>Une date d'effet doit être postérieure à celle du dernier taux validé de la même case :
 * les périodes de validité se suivent sans se chevaucher, et un taux validé n'est jamais modifié.</p>
 */
@Service
@RequiredArgsConstructor
public class BaremePerDiemService {

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Devises affichées l'une pour l'autre (barème Afrique / hors Afrique : euro ou franc CFA). */
    private static final Map<String, String> DEVISE_EQUIVALENTE = Map.of("XOF", "EUR", "EUR", "XOF");

    private final BaremePerDiemRepo baremeRepo;
    private final ReferentielMissionService referentielService;
    private final DeviseRepo deviseRepo;

    // ── Droits ───────────────────────────────────────────────────────────────

    /** Proposer un taux : RH + MAKER, ou administrateur. */
    public static boolean peutProposer(Authentication auth) {
        return Habilitations.peutCreer(auth, Habilitations.RH);
    }

    /** Valider / rejeter : RH + CHECKER (DRH), ou administrateur. */
    public static boolean peutValider(Authentication auth) {
        return Habilitations.peutValider(auth, Habilitations.RH);
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    /** Taux journalier qui s'applique à une mission (catégorie, zone) partant à la date donnée. */
    public Optional<BaremePerDiem> tauxApplicable(Integer idCategorie, Integer idZone, LocalDate date) {
        return baremeRepo.findApplicable(idCategorie, idZone, date);
    }

    /** Grille des taux validés applicables à la date : catégorie → zone → taux. */
    public Map<Integer, Map<Integer, BaremePerDiem>> grille(LocalDate date) {
        return indexer(baremeRepo.findApplicablesLe(date));
    }

    /** Propositions en attente, indexées comme la grille (repérage des cases concernées). */
    public Map<Integer, Map<Integer, BaremePerDiem>> grilleEnAttente() {
        return indexer(enAttente());
    }

    public List<BaremePerDiem> enAttente() {
        return baremeRepo.findByStatutOrdonnes(Statut.PROPOSE);
    }

    /** Taux validés qui entreront en vigueur après la date donnée. */
    public List<BaremePerDiem> programmesApres(LocalDate date) {
        return baremeRepo.findProgrammesApres(date);
    }

    public List<BaremePerDiem> historique() {
        return baremeRepo.findHistorique();
    }

    /**
     * Contre-valeur indicative de chaque taux en franc CFA ou en euro (clé : identifiant du taux),
     * au cours enregistré des devises. Ex. 90 000 XOF ≈ 137,20 EUR. Les taux dans une autre devise
     * n'ont pas de contre-valeur.
     */
    public Map<Integer, String> equivalents(Collection<BaremePerDiem> taux) {
        Map<String, Optional<Devise>> devises = new HashMap<>();
        Map<Integer, String> m = new HashMap<>();
        for (BaremePerDiem b : taux) {
            if (b.getId() == null || b.getDevise() == null || b.getDevise().getAcronyme() == null) continue;
            String cible = DEVISE_EQUIVALENTE.get(b.getDevise().getAcronyme().toUpperCase());
            if (cible == null) continue;
            devises.computeIfAbsent(cible, deviseRepo::findByAcronymeIgnoreCase)
                    .flatMap(vers -> convertir(b.getMontant(), b.getDevise(), vers))
                    .ifPresent(v -> m.put(b.getId(), String.format(Locale.FRANCE, "%,.2f", v) + " " + cible));
        }
        return m;
    }

    /** Montant converti au cours en ariary des deux devises, arrondi au centime ; vide sans cours. */
    static Optional<BigDecimal> convertir(BigDecimal montant, Devise de, Devise vers) {
        if (montant == null || de == null || vers == null || de.getCoursAriary() <= 0 || vers.getCoursAriary() <= 0) {
            return Optional.empty();
        }
        return Optional.of(montant.multiply(BigDecimal.valueOf(de.getCoursAriary()))
                .divide(BigDecimal.valueOf(vers.getCoursAriary()), 2, RoundingMode.HALF_UP));
    }

    // ── Circuit maker / checker ──────────────────────────────────────────────

    /**
     * Le RH MAKER propose un taux journalier.
     *
     * @throws IllegalArgumentException saisie invalide, proposition déjà en attente pour la même case,
     *                                  ou date d'effet antérieure au dernier taux validé
     */
    @Transactional
    public BaremePerDiem proposer(Authentication auth, Utilisateur auteur,
                                  Integer idCategorie, Integer idZone,
                                  String montant, Integer idDevise, LocalDate dateEffet, String motif) {
        if (!peutProposer(auth)) {
            throw new AccessDeniedException("Seuls les RH (MAKER) proposent un taux du barème.");
        }
        CategoriePersonnel categorie = referentielService.categorie(idCategorie);
        ZoneMission zone = referentielService.zone(idZone);
        if (!categorie.estActif()) {
            throw new IllegalArgumentException("La catégorie « " + categorie.getLibelle() + " » est désactivée.");
        }
        if (!zone.estActif()) {
            throw new IllegalArgumentException("La zone « " + zone.getLibelle() + " » est désactivée.");
        }
        BigDecimal valeur = lireMontant(montant);
        if (dateEffet == null) {
            throw new IllegalArgumentException("La date d'effet est obligatoire.");
        }

        if (!baremeRepo.findCase(categorie.getId(), zone.getId(), Statut.PROPOSE).isEmpty()) {
            throw new IllegalArgumentException("Une proposition est déjà en attente pour "
                    + designation(categorie, zone) + " : la faire valider, rejeter ou annuler d'abord.");
        }
        verifierDateEffet(categorie, zone, dateEffet);

        BaremePerDiem b = new BaremePerDiem();
        b.setCategorie(categorie);
        b.setZone(zone);
        b.setMontant(valeur);
        b.setDevise(lireDevise(idDevise));
        b.setDateEffet(dateEffet);
        b.setStatut(Statut.PROPOSE);
        b.setProposePar(auteur);
        b.setDateProposition(LocalDateTime.now());
        b.setMotif(texte(motif));
        return baremeRepo.save(b);
    }

    /**
     * Le DRH valide : le taux entre en vigueur à sa date d'effet et clôt le taux précédent de la même case.
     *
     * @throws AccessDeniedException    pas RH + CHECKER, ou validation de sa propre proposition
     * @throws IllegalArgumentException proposition déjà traitée, ou date d'effet devenue invalide
     */
    @Transactional
    public BaremePerDiem valider(Authentication auth, Utilisateur valideur, int id, String commentaire) {
        BaremePerDiem b = enAttente(id);
        if (!Habilitations.peutValiderDemandeDe(auth, Habilitations.RH, b.getProposePar(), valideur)) {
            throw new AccessDeniedException(peutValider(auth)
                    ? "Une proposition se valide par une autre personne que son auteur."
                    : "Seul le DRH (RH + CHECKER) valide le barème.");
        }
        verifierDateEffet(b.getCategorie(), b.getZone(), b.getDateEffet());

        // Clôture du taux en cours de la même case (au plus un taux ouvert, cf. verifierDateEffet).
        // Écrite en base avant que le nouveau taux devienne VALIDE : l'index unique
        // ux_bareme_per_diem_ouvert n'admet qu'un taux validé sans date de fin par case.
        for (BaremePerDiem precedent : baremeRepo.findCase(b.getCategorie().getId(), b.getZone().getId(), Statut.VALIDE)) {
            if (precedent.getDateFin() == null) {
                precedent.setDateFin(b.getDateEffet());
                baremeRepo.saveAndFlush(precedent);
            }
        }

        b.setStatut(Statut.VALIDE);
        b.setTraitePar(valideur);
        b.setDateTraitement(LocalDateTime.now());
        b.setCommentaireTraitement(texte(commentaire));
        return baremeRepo.save(b);
    }

    /** Le DRH rejette une proposition, avec un motif obligatoire. */
    @Transactional
    public BaremePerDiem rejeter(Authentication auth, Utilisateur valideur, int id, String commentaire) {
        BaremePerDiem b = enAttente(id);
        if (!Habilitations.peutValiderDemandeDe(auth, Habilitations.RH, b.getProposePar(), valideur)) {
            throw new AccessDeniedException(peutValider(auth)
                    ? "Une proposition se rejette par une autre personne que son auteur : l'annuler plutôt."
                    : "Seul le DRH (RH + CHECKER) rejette une proposition du barème.");
        }
        String motif = texte(commentaire);
        if (motif == null) {
            throw new IllegalArgumentException("Le motif du rejet est obligatoire.");
        }
        b.setStatut(Statut.REJETE);
        b.setTraitePar(valideur);
        b.setDateTraitement(LocalDateTime.now());
        b.setCommentaireTraitement(motif);
        return baremeRepo.save(b);
    }

    /** L'auteur (ou l'administrateur) retire sa proposition tant qu'elle est en attente. */
    @Transactional
    public BaremePerDiem annuler(Authentication auth, Utilisateur auteur, int id) {
        BaremePerDiem b = enAttente(id);
        boolean estAuteur = b.getProposePar() != null && auteur != null
                && b.getProposePar().getId() == auteur.getId();
        if (!estAuteur && !Habilitations.estAdmin(auth)) {
            throw new AccessDeniedException("Seul l'auteur de la proposition peut l'annuler.");
        }
        b.setStatut(Statut.ANNULE);
        b.setTraitePar(auteur);
        b.setDateTraitement(LocalDateTime.now());
        return baremeRepo.save(b);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BaremePerDiem enAttente(int id) {
        BaremePerDiem b = baremeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proposition introuvable."));
        if (!b.estEnAttente()) {
            throw new IllegalArgumentException("Cette proposition a déjà été traitée ("
                    + b.getStatut().getLibelle().toLowerCase() + ").");
        }
        return b;
    }

    /** La date d'effet suit strictement celle du dernier taux validé de la même case. */
    private void verifierDateEffet(CategoriePersonnel categorie, ZoneMission zone, LocalDate dateEffet) {
        List<BaremePerDiem> valides = baremeRepo.findCase(categorie.getId(), zone.getId(), Statut.VALIDE);
        if (!valides.isEmpty() && !dateEffet.isAfter(valides.get(0).getDateEffet())) {
            throw new IllegalArgumentException("Pour " + designation(categorie, zone)
                    + ", la date d'effet doit être postérieure au " + valides.get(0).getDateEffet().format(JJ_MM_AAAA)
                    + " (date d'effet du dernier taux validé).");
        }
    }

    private Devise lireDevise(Integer idDevise) {
        if (idDevise != null) {
            return deviseRepo.findById(idDevise)
                    .orElseThrow(() -> new IllegalArgumentException("Devise introuvable."));
        }
        return deviseRepo.findByAcronymeIgnoreCase(Devise.CODE_REFERENCE)
                .orElseThrow(() -> new IllegalArgumentException("La devise est obligatoire."));
    }

    /** « 45 000 », « 45000,50 » ou « 45000.50 » ; strictement positif, 2 décimales au plus. */
    static BigDecimal lireMontant(String montant) {
        if (montant == null || montant.isBlank()) {
            throw new IllegalArgumentException("Le montant est obligatoire.");
        }
        String s = montant.replaceAll("[\\s\\u00A0\\u202F]", "").replace(',', '.');
        BigDecimal m;
        try {
            m = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Montant invalide : " + montant);
        }
        if (m.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à zéro.");
        }
        if (m.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Le montant a au plus deux décimales.");
        }
        if (m.precision() - m.scale() > 13) {
            throw new IllegalArgumentException("Montant trop élevé.");
        }
        return m;
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String designation(CategoriePersonnel c, ZoneMission z) {
        return c.getLibelle() + " / " + z.getLibelle();
    }

    private static Map<Integer, Map<Integer, BaremePerDiem>> indexer(List<BaremePerDiem> taux) {
        Map<Integer, Map<Integer, BaremePerDiem>> m = new HashMap<>();
        for (BaremePerDiem b : taux) {
            m.computeIfAbsent(b.getCategorie().getId(), k -> new HashMap<>()).put(b.getZone().getId(), b);
        }
        return m;
    }
}
