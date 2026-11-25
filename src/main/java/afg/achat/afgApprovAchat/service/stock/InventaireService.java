package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.email.EmailSenderService;
import afg.achat.afgApprovAchat.email.Mail;
import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.model.stock.inventaire.Inventaire;
import afg.achat.afgApprovAchat.model.stock.inventaire.Inventaire.Statut;
import afg.achat.afgApprovAchat.model.stock.inventaire.InventaireEvenement;
import afg.achat.afgApprovAchat.model.stock.inventaire.InventaireEvenement.Action;
import afg.achat.afgApprovAchat.model.stock.inventaire.InventaireLigne;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import afg.achat.afgApprovAchat.repository.FamilleRepo;
import afg.achat.afgApprovAchat.repository.stock.LotStockRepo;
import afg.achat.afgApprovAchat.repository.stock.StockMereRepo;
import afg.achat.afgApprovAchat.repository.stock.inventaire.InventaireLigneRepo;
import afg.achat.afgApprovAchat.repository.stock.inventaire.InventaireRepo;
import afg.achat.afgApprovAchat.service.securite.Habilitations;
import afg.achat.afgApprovAchat.service.util.PrixArticleService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Inventaires physiques du stock, en circuit maker / checker des Moyens Généraux.
 *
 * <ul>
 *   <li><b>Ouverture</b> (Moyens Généraux) : une ligne par article actif du périmètre (complet ou
 *       une famille), avec le stock théorique du moment.</li>
 *   <li><b>Comptage</b> : saisie des quantités comptées, enregistrable en plusieurs fois. Le stock
 *       théorique d'une ligne est relu à chaque saisie de sa quantité.</li>
 *   <li><b>Soumission</b> : tous les articles doivent être comptés.</li>
 *   <li><b>Validation</b> (MOYENS_GENERAUX + CHECKER, autre personne que l'auteur et le compteur) :
 *       chaque écart passe en stock ; un écart négatif sort des lots FIFO, un écart positif crée un
 *       lot au dernier prix connu. Le stock n'est jamais modifié avant.</li>
 *   <li><b>Rejet</b> : retour en comptage avec un motif ; <b>annulation</b> par l'auteur avant validation.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventaireService {

    private final InventaireRepo inventaireRepo;
    private final InventaireLigneRepo ligneRepo;
    private final ArticleRepo articleRepo;
    private final FamilleRepo familleRepo;
    private final LotStockRepo lotStockRepo;
    private final LotStockService lotStockService;
    private final StockMereRepo stockMereRepo;
    private final MouvementStockService mouvementStockService;
    private final PrixArticleService prixArticleService;
    private final UtilisateurService utilisateurService;
    private final EmailSenderService emailSenderService;

    /** Synthèse d'un inventaire pour les listes et la fiche. */
    public record Synthese(long lignes, long comptees, long ecarts, double valeurPlus, double valeurMoins) {
        public static final Synthese VIDE = new Synthese(0, 0, 0, 0, 0);
        public long getLignes()        { return lignes; }
        public long getComptees()      { return comptees; }
        public long getEcarts()        { return ecarts; }
        public double getValeurPlus()  { return valeurPlus; }
        public double getValeurMoins() { return valeurMoins; }
        public double getValeurNette() { return valeurPlus + valeurMoins; }
        public int getAvancement()     { return lignes == 0 ? 0 : (int) Math.floor(comptees * 100.0 / lignes); }
    }

    // ── Droits ───────────────────────────────────────────────────────────────

    /** Ouvrir, compter, soumettre : administrateur, Moyens Généraux (avec ou sans MAKER). */
    public static boolean peutSaisir(Authentication auth) {
        return Habilitations.peutCreer(auth, Habilitations.MG, Habilitations.MG);
    }

    /** Valider / rejeter : administrateur ou MOYENS_GENERAUX + CHECKER. */
    public static boolean peutValider(Authentication auth) {
        return Habilitations.peutValider(auth, Habilitations.MG);
    }

    /** Valider ou rejeter cet inventaire : soumis, valideur habilité, autre personne que l'auteur et le compteur. */
    public boolean peutDecider(Inventaire inv, Utilisateur u, Authentication auth) {
        if (inv == null || !inv.estSoumis() || !peutValider(auth)) return false;
        if (Habilitations.estAdmin(auth)) return true;
        return !estLaMemePersonne(inv.getCreePar(), u) && !estLaMemePersonne(inv.getSoumisPar(), u);
    }

    public boolean peutCompter(Inventaire inv, Authentication auth) {
        return inv != null && inv.estEnCours() && peutSaisir(auth);
    }

    /** Annuler : l'auteur de l'inventaire ou un administrateur, tant qu'il n'est pas validé. */
    public boolean peutAnnuler(Inventaire inv, Utilisateur u, Authentication auth) {
        return inv != null && inv.estOuvert()
                && (Habilitations.estAdmin(auth) || (peutSaisir(auth) && estLaMemePersonne(inv.getCreePar(), u)));
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    public Inventaire getInventaire(int id) {
        return inventaireRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventaire introuvable : " + id));
    }

    /**
     * Historique filtré, du plus récent au plus ancien.
     *
     * @param familleId famille ; {@code 0} : inventaires complets ; {@code null} : tous
     */
    public Page<Inventaire> rechercher(Statut statut, Integer familleId, LocalDate du, LocalDate au,
                                       String reference, Pageable pageable) {
        Specification<Inventaire> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (statut != null) p.add(cb.equal(root.get("statut"), statut));
            if (familleId != null) {
                p.add(familleId == 0 ? cb.isNull(root.get("famille"))
                        : cb.equal(root.get("famille").get("id"), familleId));
            }
            if (du != null) p.add(cb.greaterThanOrEqualTo(root.get("dateOuverture"), du.atStartOfDay()));
            if (au != null) p.add(cb.lessThan(root.get("dateOuverture"), au.plusDays(1).atStartOfDay()));
            if (reference != null && !reference.isBlank()) {
                p.add(cb.like(cb.lower(root.get("reference")), "%" + reference.trim().toLowerCase() + "%"));
            }
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(root.get("dateOuverture")), cb.desc(root.get("id")));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return inventaireRepo.findAll(spec, pageable);
    }

    public Map<Integer, Synthese> syntheses(Collection<Integer> ids) {
        Map<Integer, Synthese> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) return m;
        for (Object[] r : ligneRepo.syntheses(ids)) {
            m.put((Integer) r[0], new Synthese(nombre(r[1]), nombre(r[2]), nombre(r[3]), decimal(r[4]), decimal(r[5])));
        }
        return m;
    }

    public Synthese synthese(Inventaire inv) {
        return syntheses(List.of(inv.getId())).getOrDefault(inv.getId(), Synthese.VIDE);
    }

    /** Inventaires en cours ou soumis (le stock n'est pas encore ajusté). */
    public List<Inventaire> ouverts() {
        return inventaireRepo.findByStatutInOrderByDateOuvertureAsc(List.of(Statut.EN_COURS, Statut.SOUMIS));
    }

    public long nombreAValider() {
        return inventaireRepo.countByStatut(Statut.SOUMIS);
    }

    /** Stock disponible (lots FIFO) des articles de l'inventaire, pour l'affichage du stock du jour. */
    public Map<Integer, Double> stocksActuels(Inventaire inv) {
        return stocks(inv.getLignes().stream().map(l -> l.getArticle().getId()).toList());
    }

    // ── Ouverture ────────────────────────────────────────────────────────────

    /** @param familleId famille à inventorier ; {@code null} : inventaire complet */
    @Transactional
    public Inventaire ouvrir(Integer familleId, String commentaire, Utilisateur auteur) {
        Famille famille = null;
        if (familleId != null) {
            famille = familleRepo.findById(familleId)
                    .orElseThrow(() -> new IllegalArgumentException("Famille introuvable : " + familleId));
        }
        verifierPerimetreLibre(famille);

        List<Article> articles = articleRepo.findActifsParFamille(familleId);
        if (articles.isEmpty()) {
            throw new IllegalArgumentException("Aucun article actif dans ce périmètre : rien à inventorier.");
        }
        Map<Integer, Double> stocks = stocks(articles.stream().map(Article::getId).toList());

        Inventaire inv = new Inventaire();
        inv.setFamille(famille);
        inv.setStatut(Statut.EN_COURS);
        inv.setCommentaire(texte(commentaire));
        inv.setCreePar(auteur);
        inv.setDateOuverture(LocalDateTime.now());
        for (Article a : articles) {
            InventaireLigne l = new InventaireLigne();
            l.setInventaire(inv);
            l.setArticle(a);
            l.setStockTheorique(stocks.getOrDefault(a.getId(), 0.0));
            inv.getLignes().add(l);
        }
        journaliser(inv, Action.OUVERTURE, auteur, null);
        inv = inventaireRepo.save(inv);
        inv.setReference(String.format("INV-%d-%04d", inv.getDateOuverture().getYear(), inv.getId()));
        return inventaireRepo.save(inv);
    }

    /**
     * Un seul inventaire ouvert par périmètre : un complet exclut tout autre, et une famille
     * ne peut pas être comptée deux fois en même temps.
     */
    private void verifierPerimetreLibre(Famille famille) {
        for (Inventaire o : ouverts()) {
            boolean conflit = o.getFamille() == null || famille == null || o.getFamille().getId() == famille.getId();
            if (conflit) {
                throw new IllegalArgumentException("L'inventaire " + o.getReference() + " (" + o.getLibellePerimetre()
                        + ") est encore ouvert sur ce périmètre : il doit être validé ou annulé avant d'en ouvrir un autre.");
            }
        }
    }

    // ── Comptage ─────────────────────────────────────────────────────────────

    /**
     * Enregistre les quantités comptées (vide : pas encore compté) et les justifications.
     * Le stock théorique d'une ligne est relu quand sa quantité change : l'écart porte sur le
     * stock au moment du comptage, pas sur celui de l'ouverture.
     *
     * @return nombre de lignes modifiées
     */
    @Transactional
    public int enregistrerComptage(int id, Map<Integer, String> quantites, Map<Integer, String> commentaires,
                                   Utilisateur auteur) {
        Inventaire inv = inventaireRepo.verrouiller(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventaire introuvable : " + id));
        if (!inv.estEnCours()) {
            throw new IllegalArgumentException("L'inventaire " + inv.getReference() + " n'est plus en comptage ("
                    + inv.getStatut().getLibelle().toLowerCase() + ").");
        }

        List<String> erreurs = new ArrayList<>();
        List<InventaireLigne> recomptees = new ArrayList<>();
        int modifiees = 0;
        for (InventaireLigne l : inv.getLignes()) {
            boolean modifiee = false;
            if (quantites.containsKey(l.getId())) {
                Double q;
                try {
                    q = quantite(quantites.get(l.getId()));
                } catch (IllegalArgumentException e) {
                    erreurs.add(l.getArticle().getCodeArticle() + " : " + e.getMessage());
                    continue;
                }
                if (!Objects.equals(q, l.getQuantiteComptee())) {
                    l.setQuantiteComptee(q);
                    l.setDateComptage(q == null ? null : LocalDateTime.now());
                    if (q != null) recomptees.add(l);
                    modifiee = true;
                }
            }
            if (commentaires.containsKey(l.getId())) {
                String c = texte(commentaires.get(l.getId()));
                if (!Objects.equals(c, l.getCommentaire())) {
                    l.setCommentaire(c);
                    modifiee = true;
                }
            }
            if (modifiee) modifiees++;
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException("Quantités invalides — " + String.join(" ; ", erreurs));
        }

        // Stock théorique relu au moment du comptage
        Map<Integer, Double> stocks = stocks(recomptees.stream().map(l -> l.getArticle().getId()).toList());
        recomptees.forEach(l -> l.setStockTheorique(stocks.getOrDefault(l.getArticle().getId(), 0.0)));

        inventaireRepo.save(inv);
        return modifiees;
    }

    // ── Soumission ───────────────────────────────────────────────────────────

    @Transactional
    public Inventaire soumettre(int id, Utilisateur auteur) {
        Inventaire inv = inventaireRepo.verrouiller(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventaire introuvable : " + id));
        if (!inv.estEnCours()) {
            throw new IllegalArgumentException("Seul un inventaire en cours de comptage peut être soumis.");
        }
        long nonComptees = inv.getLignes().stream().filter(l -> !l.estCompte()).count();
        if (nonComptees > 0) {
            throw new IllegalArgumentException(nonComptees + " article(s) ne sont pas encore comptés. "
                    + "Saisissez 0 pour un article absent du magasin.");
        }
        inv.setStatut(Statut.SOUMIS);
        inv.setSoumisPar(auteur);
        inv.setDateSoumission(LocalDateTime.now());
        journaliser(inv, Action.SOUMISSION, auteur, null);
        return notifierCheckers(inventaireRepo.save(inv));
    }

    // ── Décision ─────────────────────────────────────────────────────────────

    /**
     * Valide l'inventaire et passe ses écarts en stock, en une transaction : si un écart négatif
     * dépasse le stock du jour (sorties depuis le comptage), rien n'est appliqué.
     */
    @Transactional
    public Inventaire valider(int id, Utilisateur valideur, Authentication auth) {
        Inventaire inv = inventaireRepo.verrouiller(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventaire introuvable : " + id));
        exigerDroitDeDecision(inv, valideur, auth);

        List<InventaireLigne> ecarts = inv.getLignes().stream().filter(InventaireLigne::aEcart).toList();

        // Articles verrouillés (comme une sortie) puis contrôle complet avant toute écriture
        ecarts.stream().map(l -> l.getArticle().getCodeArticle()).sorted().forEach(articleRepo::lockArticle);
        List<String> impossibles = new ArrayList<>();
        for (InventaireLigne l : ecarts) {
            if (l.getEcart() < 0) {
                double stock = lotStockService.getStockDisponible(l.getArticle().getCodeArticle());
                if (-l.getEcart() > stock + InventaireLigne.TOLERANCE) {
                    impossibles.add(l.getArticle().getCodeArticle() + " (écart " + format(l.getEcart())
                            + ", stock actuel " + format(stock) + ")");
                }
            }
        }
        if (!impossibles.isEmpty()) {
            throw new IllegalArgumentException("Des sorties ont eu lieu depuis le comptage : l'écart dépasse le stock actuel pour "
                    + String.join(", ", impossibles) + ". Rejetez l'inventaire pour faire recompter ces articles.");
        }

        if (!ecarts.isEmpty()) {
            StockMere sm = new StockMere();
            sm.setInventaire(inv);
            stockMereRepo.save(sm);
            for (InventaireLigne l : ecarts) {
                Article a = l.getArticle();
                double q = Math.abs(l.getEcart());
                if (l.getEcart() > 0) {
                    double prix = dernierPrix(a);
                    l.setPrixUnitaire(prix);
                    mouvementStockService.entrer(sm, a, q, prix, null, null);
                } else {
                    // Valorisé au coût FIFO des lots qui sortent
                    l.setPrixUnitaire(lotStockService.getPrixFifo(a.getCodeArticle(), q));
                    mouvementStockService.sortir(sm, a, q);
                }
            }
        }

        inv.setStatut(Statut.VALIDE);
        inv.setValidePar(valideur);
        inv.setDateCloture(LocalDateTime.now());
        journaliser(inv, Action.VALIDATION, valideur,
                ecarts.isEmpty() ? "Aucun écart" : ecarts.size() + " écart(s) passé(s) en stock");
        return notifierMaker(inventaireRepo.save(inv), true);
    }

    /** Renvoie l'inventaire en comptage, avec un motif obligatoire. */
    @Transactional
    public Inventaire rejeter(int id, Utilisateur valideur, Authentication auth, String motif) {
        Inventaire inv = inventaireRepo.verrouiller(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventaire introuvable : " + id));
        exigerDroitDeDecision(inv, valideur, auth);
        String m = texte(motif);
        if (m == null) throw new IllegalArgumentException("Le motif du rejet est obligatoire.");
        inv.setStatut(Statut.EN_COURS);
        inv.setMotifRejet(m);
        journaliser(inv, Action.REJET, valideur, m);
        return notifierMaker(inventaireRepo.save(inv), false);
    }

    @Transactional
    public Inventaire annuler(int id, Utilisateur auteur, Authentication auth, String motif) {
        Inventaire inv = inventaireRepo.verrouiller(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventaire introuvable : " + id));
        if (!peutAnnuler(inv, auteur, auth)) {
            throw new IllegalArgumentException(inv.estOuvert()
                    ? "Seul l'auteur de l'inventaire (ou un administrateur) peut l'annuler."
                    : "Un inventaire " + inv.getStatut().getLibelle().toLowerCase() + " ne peut plus être annulé.");
        }
        inv.setStatut(Statut.ANNULE);
        inv.setValidePar(auteur);
        inv.setDateCloture(LocalDateTime.now());
        journaliser(inv, Action.ANNULATION, auteur, texte(motif));
        return inventaireRepo.save(inv);
    }

    private void exigerDroitDeDecision(Inventaire inv, Utilisateur valideur, Authentication auth) {
        if (!inv.estSoumis()) {
            throw new IllegalArgumentException("L'inventaire " + inv.getReference() + " n'attend pas de validation ("
                    + inv.getStatut().getLibelle().toLowerCase() + ").");
        }
        if (!peutValider(auth)) {
            throw new IllegalArgumentException("La validation des inventaires est réservée au rôle "
                    + Habilitations.libelle(Habilitations.roleChecker(Habilitations.MG)) + ".");
        }
        if (!peutDecider(inv, valideur, auth)) {
            throw new IllegalArgumentException("Vous avez ouvert ou compté cet inventaire : une autre personne doit le valider.");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<Integer, Double> stocks(Collection<Integer> articleIds) {
        Map<Integer, Double> m = new HashMap<>();
        if (articleIds.isEmpty()) return m;
        // Par paquets : la liste IN reste raisonnable pour un inventaire complet
        List<Integer> ids = new ArrayList<>(articleIds);
        for (int i = 0; i < ids.size(); i += 500) {
            for (Object[] r : lotStockRepo.stocksDisponibles(ids.subList(i, Math.min(i + 500, ids.size())))) {
                m.put((Integer) r[0], decimal(r[1]));
            }
        }
        return m;
    }

    /** Dernier prix d'achat connu : dernier lot à prix non nul, sinon dernier prix article, sinon 0. */
    private double dernierPrix(Article a) {
        return lotStockRepo.findFirstByArticle_IdAndPrixUnitaireGreaterThanOrderByDateEntreeDesc(a.getId(), 0)
                .map(l -> l.getPrixUnitaire())
                .or(() -> prixArticleService.getDernierPrixByArticle(a.getCodeArticle()).map(p -> p.getPrixUnitaire()))
                .orElse(0.0);
    }

    private static void journaliser(Inventaire inv, Action action, Utilisateur auteur, String commentaire) {
        inv.getEvenements().add(new InventaireEvenement(inv, action, auteur, commentaire));
    }

    private static Double quantite(String valeur) {
        if (valeur == null || valeur.isBlank()) return null;
        double q;
        try {
            q = Double.parseDouble(valeur.trim().replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("« " + valeur.trim() + " » n'est pas une quantité");
        }
        if (q < 0 || Double.isNaN(q) || Double.isInfinite(q)) {
            throw new IllegalArgumentException("la quantité comptée ne peut pas être négative");
        }
        return q;
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean estLaMemePersonne(Utilisateur a, Utilisateur b) {
        return a != null && b != null && a.getId() == b.getId();
    }

    private static long nombre(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static double decimal(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static String format(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.FRANCE, "%.3f", d);
    }

    // ── Notifications (après le commit : un échec d'envoi ne remet rien en cause) ──

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    /** Prévient les MOYENS_GENERAUX + CHECKER (sauf l'auteur et le compteur) qu'un inventaire attend. */
    private Inventaire notifierCheckers(Inventaire inv) {
        Map<String, Utilisateur> destinataires = new LinkedHashMap<>();
        for (Utilisateur u : utilisateurService.getUtilisateursByRole(Habilitations.roleChecker(Habilitations.MG))) {
            if (u.getMail() == null || u.getMail().isBlank()) continue;
            if (estLaMemePersonne(u, inv.getCreePar()) || estLaMemePersonne(u, inv.getSoumisPar())) continue;
            destinataires.putIfAbsent(u.getMail().trim().toLowerCase(), u);
        }
        if (destinataires.isEmpty()) {
            log.warn("Inventaire {} : aucun valideur (MOYENS_GENERAUX + CHECKER) à prévenir.", inv.getReference());
            return inv;
        }
        Synthese s = synthese(inv);
        String sujet = "[AFG Bank - Achat] - Inventaire à valider : " + inv.getReference()
                + " — " + inv.getLibellePerimetre();
        List<Mail> mails = new ArrayList<>();
        for (Utilisateur dest : destinataires.values()) {
            Map<String, Object> props = new HashMap<>();
            props.put("destinataire", dest);
            props.put("maker", inv.getSoumisPar());
            props.put("reference", inv.getReference());
            props.put("perimetre", inv.getLibellePerimetre());
            props.put("lignes", s.lignes());
            props.put("ecarts", inv.getLignes().stream().filter(InventaireLigne::aEcart).count());
            props.put("dateSoumission", inv.getDateSoumission().format(FORMAT_DATE));
            props.put("lien", emailSenderService.lien("/stock/inventaire/" + inv.getId()));
            mails.add(new Mail("inventaireSoumis", dest.getMail(), sujet, props));
        }
        apresCommit(() -> mails.forEach(emailSenderService::sendEmail));
        return inv;
    }

    /** Prévient l'auteur (et le compteur) de la décision. */
    private Inventaire notifierMaker(Inventaire inv, boolean valide) {
        Map<String, Utilisateur> destinataires = new LinkedHashMap<>();
        for (Utilisateur u : new Utilisateur[]{inv.getCreePar(), inv.getSoumisPar()}) {
            if (u != null && u.getMail() != null && !u.getMail().isBlank()) {
                destinataires.putIfAbsent(u.getMail().trim().toLowerCase(), u);
            }
        }
        if (destinataires.isEmpty()) return inv;
        InventaireEvenement decision = inv.getEvenements().get(inv.getEvenements().size() - 1);
        List<Mail> mails = new ArrayList<>();
        for (Utilisateur dest : destinataires.values()) {
            Map<String, Object> props = new HashMap<>();
            props.put("destinataire", dest);
            props.put("checker", decision.getAuteur());
            props.put("valide", valide);
            props.put("reference", inv.getReference());
            props.put("perimetre", inv.getLibellePerimetre());
            props.put("motif", valide ? null : inv.getMotifRejet());
            props.put("dateDecision", decision.getDate().format(FORMAT_DATE));
            props.put("lien", emailSenderService.lien("/stock/inventaire/" + inv.getId()));
            mails.add(new Mail("inventaireDecision", dest.getMail(),
                    "[AFG Bank - Achat] - Inventaire " + (valide ? "validé" : "rejeté") + " : " + inv.getReference(),
                    props));
        }
        apresCommit(() -> mails.forEach(emailSenderService::sendEmail));
        return inv;
    }

    private static void apresCommit(Runnable action) {
        Runnable sansEchec = () -> {
            try {
                action.run();
            } catch (Exception e) {
                log.error("Échec de l'envoi des notifications d'inventaire", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sansEchec.run();
                }
            });
        } else {
            sansEchec.run();
        }
    }
}
