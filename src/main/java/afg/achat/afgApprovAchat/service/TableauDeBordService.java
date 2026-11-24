package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.controller.StockController;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.EtapeWorkflow;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import afg.achat.afgApprovAchat.repository.demande.ValidationDemandeRepo;
import afg.achat.afgApprovAchat.service.bonCommande.BesoinAchatService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.stock.StockAlerteService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Contenu du tableau de bord autre que les statistiques de demandes :
 * <ul>
 *   <li>« À traiter » : ce qui attend une action de l'utilisateur connecté, selon ses rôles ;</li>
 *   <li>« Mes demandes » : ses dernières demandes et ses refus récents ;</li>
 *   <li>indicateurs d'achats (montants, fournisseurs, réception, stock, délais par étape).</li>
 * </ul>
 * Chaque compteur est une requête d'agrégat (aucune liste chargée en mémoire) et applique
 * les mêmes règles d'accès que la page vers laquelle il mène.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableauDeBordService {

    /** Au-delà, un élément en attente est signalé comme en retard. */
    public static final int JOURS_AVANT_RETARD = 3;

    private final DemandeMereRepo demandeMereRepo;
    private final ValidationDemandeRepo validationDemandeRepo;
    private final BonCommandeMereRepo bonCommandeMereRepo;
    private final BesoinAchatService besoinAchatService;
    private final StockAlerteService stockAlerteService;
    private final UtilisateurService utilisateurService;
    private final SortieAFaireService sortieAFaireService;
    private final afg.achat.afgApprovAchat.service.utilisateur.DemandeCompteService demandeCompteService;

    // ════════════════════════════════════════════════════════════════════════
    // 1. À traiter
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Un élément à traiter.
     *
     * @param joursAttente ancienneté du plus ancien élément, en jours ({@code null} si sans objet)
     * @param urgent       à mettre en évidence (attente longue, livraison en retard, bon rejeté…)
     */
    public record Action(String titre, String detail, String icone, long nombre,
                         Long joursAttente, boolean urgent, String url) {
    }

    public List<Action> actions(Utilisateur current, Authentication auth) {
        List<Action> actions = new ArrayList<>();
        LocalDateTime maintenant = LocalDateTime.now();
        boolean admin = role(auth, "ADMIN");
        boolean mg = role(auth, "MOYENS_GENERAUX");

        // ── Demandes : N+1 et validateur assigné ──
        Set<Integer> collaborateurs = new HashSet<>(utilisateurService.getIdsUtilisateurVisible(current.getId()));
        collaborateurs.remove(current.getId());
        collaborateurs.addAll(utilisateurService.getIdsUtilisateursAValider(current.getId()));
        if (!collaborateurs.isEmpty()) {
            Object[] r = premiere(demandeMereRepo.compterStatutPourDemandeurs(StatutDemande.CREE, collaborateurs));
            ajouterSiNonVide(actions, "Demandes de vos collaborateurs", "À valider en tant que N+1 ou validateur",
                    "fa-user-clock", nombre(r[0]), jours(r[1], maintenant), "/demande/list?statut=" + StatutDemande.CREE);
        }

        // ── Demandes : étapes décidées par un rôle (M.G., Contrôle, D.F.C., S.G., CODEP) ──
        Map<Integer, EtapeWorkflow> etapesDuRole = new LinkedHashMap<>();
        for (EtapeWorkflow e : EtapeWorkflow.values()) {
            if (e.getRoleValidateur() != null && role(auth, e.getRoleValidateur())) {
                etapesDuRole.put(e.getStatut(), e);
            }
        }
        if (!etapesDuRole.isEmpty()) {
            for (Object[] r : demandeMereRepo.compterParStatut(etapesDuRole.keySet())) {
                EtapeWorkflow e = etapesDuRole.get(((Number) r[0]).intValue());
                boolean codep = e == EtapeWorkflow.DECISION_CODEP;
                ajouterSiNonVide(actions,
                        codep ? "Décisions CODEP à enregistrer" : "Demandes à valider",
                        e.getLibelleAttente(),
                        codep ? "fa-landmark" : "fa-clipboard-check",
                        nombre(r[1]), jours(r[2], maintenant), "/demande/list?statut=" + e.getStatut());
            }
        }

        // ── Comptes utilisateurs à valider (IT_CHECKER, hors ses propres demandes) ──
        if (afg.achat.afgApprovAchat.service.securite.Habilitations.peutValider(auth,
                afg.achat.afgApprovAchat.service.securite.Habilitations.IT)) {
            Object[] r = demandeCompteService.aValiderPar(current);
            ajouterSiNonVide(actions, "Demandes de compte à valider", "Créations et modifications d'utilisateurs",
                    "fa-user-check", nombre(r[0]), jours(r[1], maintenant), "/user/demandes");
        }

        // ── Bons de commande à valider (MG_CHECKER, SG) ──
        Set<BonCommandeMere.EtapeValidation> etapesBc = EnumSet.noneOf(BonCommandeMere.EtapeValidation.class);
        for (BonCommandeMere.EtapeValidation e : BonCommandeMere.EtapeValidation.values()) {
            if (admin || role(auth, e.getRole())) etapesBc.add(e);
        }
        if (!etapesBc.isEmpty()) {
            long n = 0;
            LocalDateTime plusAncien = null;
            for (Object[] r : bonCommandeMereRepo.compterEnAttenteParEtape(
                    BonCommandeMere.StatutBonCommande.EN_ATTENTE_VALIDATION)) {
                if (r[0] == null || !etapesBc.contains((BonCommandeMere.EtapeValidation) r[0])) continue;
                n += nombre(r[1]);
                plusAncien = min(plusAncien, (LocalDateTime) r[2]);
            }
            ajouterSiNonVide(actions, "Bons de commande à valider", "En attente de votre validation",
                    "fa-file-signature", n, jours(plusAncien, maintenant), "/bon-commande/list?aValider=true");
        }

        // ── Achats (création des bons de commande) ──
        if (admin || mg || role(auth, "MG_MAKER")) {
            long lignes = besoinAchatService.lignesEnAttente(
                    new BesoinAchatService.Recherche(null, null, null, null, null, null),
                    PageRequest.of(0, 1)).getTotalElements();
            ajouterSiNonVide(actions, "Lignes à commander", "Demandes validées sans bon de commande",
                    "fa-shopping-cart", lignes, null, false, "/bon-commande/lignes-a-acheter");

            Object[] r = premiere(bonCommandeMereRepo.compterParStatutAvecRejets(
                    BonCommandeMere.StatutBonCommande.BROUILLON));
            long brouillons = nombre(r[0]);
            long rejetes = nombre(r[1]);
            ajouterSiNonVide(actions, "Bons de commande en brouillon",
                    rejetes > 0 ? "Dont " + rejetes + " rejeté(s) à corriger" : "À compléter puis soumettre",
                    "fa-file-alt", brouillons, jours(r[2], maintenant), rejetes > 0, "/bon-commande/list?statut=BROUILLON");
        }

        // ── Moyens Généraux : réception, bons de sortie à faire, paiements directs ──
        // (pas de compteur de bons de sortie en brouillon : un bon est créé et confirmé
        // dans une seule transaction, un brouillon n'est qu'un reste de création interrompue)
        if (admin || mg) {
            Object[] r = premiere(bonCommandeMereRepo.compterAReceptionner(
                    BonCommandeMere.StatutBonCommande.VALIDE, BonCommandeService.RECEPTIONS_OUVERTES, maintenant));
            long retard = nombre(r[1]);
            ajouterSiNonVide(actions, "Bons de commande à réceptionner",
                    retard > 0 ? "Dont " + retard + " en retard de livraison" : "Livraison attendue",
                    "fa-truck-loading", nombre(r[0]), null, retard > 0, "/bon-commande/list?aRecevoir=true");

            // Demandes de matériel validées dont des articles à sortir sont de nouveau en stock
            // (après réception des achats, par exemple)
            ajouterSiNonVide(actions, "Bons de sortie à faire",
                    "Demandes de matériel validées avec du stock disponible", "fa-dolly",
                    sortieAFaireService.nombrePretes(), null, false, "/bon-sortie/a-faire");
        }

        // ── Validateur M.G. : paiements directs à transmettre à la finance ──
        if (WorkflowService.peutTransmettreFinance(auth)) {
            Object[] r = premiere(demandeMereRepo.compterPaiementsDirects(StatutDemande.VALIDE,
                    DemandeMere.StatutTransmissionFinance.A_TRANSMETTRE));
            ajouterSiNonVide(actions, "Paiements directs à transmettre", "À envoyer à la Finance",
                    "fa-paper-plane", nombre(r[0]), jours(r[1], maintenant),
                    "/demande/paiements-directs?statutTransmission=A_TRANSMETTRE");
        }

        // ── Finance : paiements à effectuer ──
        if (WorkflowService.hasFinanceAccess(auth)) {
            Object[] r = premiere(demandeMereRepo.compterPaiementsDirects(StatutDemande.VALIDE,
                    DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE));
            ajouterSiNonVide(actions, "Paiements directs à payer", "Transmis par les Moyens Généraux",
                    "fa-money-check-alt", nombre(r[0]), jours(r[1], maintenant),
                    "/demande/paiements-directs?statutTransmission=TRANSMISE_FINANCE");
        }

        // ── Stock (même périmètre que l'état de stock) ──
        if (admin || mg || role(auth, "IT")) {
            long alertes = (admin || mg)
                    ? stockAlerteService.countAlertes()
                    : stockAlerteService.getAlertesCountByFamilleId(StockController.FAMILLE_MATERIELS_INFORMATIQUE_ID);
            ajouterSiNonVide(actions, "Articles en alerte de stock", "En rupture ou sous le seuil minimum",
                    "fa-exclamation-triangle", alertes, null, true, "/stock/etat-stock?etat=ALERTE");
        }

        return actions;
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Mes demandes
    // ════════════════════════════════════════════════════════════════════════

    /** Une demande de l'utilisateur connecté, avec son étape actuelle. */
    public record MaDemande(String id, LocalDateTime date, String motif, String etape, String badge) {
    }

    /** Un refus reçu récemment : à quelle étape, par qui et pourquoi. */
    public record Refus(String demandeId, String motif, LocalDateTime date, String etape,
                        String par, String commentaire) {
    }

    public List<MaDemande> mesDernieresDemandes(Utilisateur current) {
        return demandeMereRepo.findTop5ByDemandeurIdOrderByDateDemandeDesc(current.getId()).stream()
                .map(d -> {
                    EtapeWorkflow e = EtapeWorkflow.fromStatut(d.getStatut());
                    String badge = e == EtapeWorkflow.VALIDE ? "bg-success"
                            : e == EtapeWorkflow.REFUSE ? "bg-danger"
                            : e == EtapeWorkflow.BROUILLON ? "bg-secondary" : "bg-warning text-dark";
                    String libelle = e == null ? "—"
                            : e == EtapeWorkflow.VALIDE ? "Validée"
                            : e == EtapeWorkflow.REFUSE ? "Refusée" : e.getLibelleAttente();
                    return new MaDemande(d.getId(), d.getDateDemande(), d.getMotifEvoque(), libelle, badge);
                })
                .toList();
    }

    /** Refus des 30 derniers jours, un par demande (le plus récent). */
    public List<Refus> mesRefusRecents(Utilisateur current) {
        Map<String, Refus> parDemande = new LinkedHashMap<>();
        for (ValidationDemande v : validationDemandeRepo.refusRecus(current.getId(),
                LocalDateTime.now().minusDays(30), ValidationDemande.DecisionValidation.REFUSE)) {
            DemandeMere d = v.getDemandeMere();
            EtapeWorkflow e = v.getEtape() == null ? null : EtapeWorkflow.fromStatut(v.getEtape());
            parDemande.putIfAbsent(d.getId(), new Refus(
                    d.getId(), d.getMotifEvoque(), v.getDateAction(),
                    e == null || e.getLibelleValidateur() == null ? "—" : e.getLibelleValidateur(),
                    v.getValidateur() == null ? "—" : v.getValidateur().getPrenom() + " " + v.getValidateur().getNom(),
                    v.getCommentaire()));
        }
        return parDemande.values().stream().limit(5).toList();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Indicateurs d'achats et de délais (encadrement)
    // ════════════════════════════════════════════════════════════════════════

    public record Fournisseur(String nom, String devise, long nombreBons, double montant) {
    }

    /** Achats engagés dans une devise : jamais additionnés avec ceux d'une autre devise. */
    public record MontantDevise(String devise, long nombreBc, double montantTtc) {
        public double montantMoyen() { return nombreBc == 0 ? 0 : montantTtc / nombreBc; }
    }

    /** Durée moyenne passée à une étape de validation, sur les demandes qui l'ont franchie. */
    /** @param largeur part du délai le plus long (100 = étape la plus lente), pour la barre affichée */
    public record DelaiEtape(String etape, double joursMoyens, long nombre, int largeur) {
        public boolean plusLente() { return largeur == 100; }
    }

    /** @param montants achats engagés par devise, l'ariary en premier */
    public record Indicateurs(long nombreBc, List<MontantDevise> montants,
                              List<Fournisseur> topFournisseurs,
                              long bcValides, long bcRecus, Double tauxReception,
                              long bcEnRetard, long alertesStock,
                              List<DelaiEtape> delais) {
    }

    /** Ordre d'affichage des étapes (ordre du circuit). */
    private static final List<EtapeWorkflow> CIRCUIT = List.of(
            EtapeWorkflow.CREE, EtapeWorkflow.VALIDATION_N1, EtapeWorkflow.DECISION_CODEP,
            EtapeWorkflow.VALIDATION_N2, EtapeWorkflow.VALIDATION_N3, EtapeWorkflow.VALIDATION_N4);

    public Indicateurs indicateurs(LocalDate du, LocalDate au) {
        LocalDateTime debut = du.atStartOfDay();
        LocalDateTime fin = au.atTime(LocalTime.MAX);
        BonCommandeMere.StatutBonCommande valide = BonCommandeMere.StatutBonCommande.VALIDE;

        // Par devise ; les bons antérieurs (sans devise) sont en ariary
        Map<String, double[]> parDevise = new java.util.TreeMap<>();
        for (Object[] r : bonCommandeMereRepo.totauxValides(valide, debut, fin)) {
            double[] cumul = parDevise.computeIfAbsent(codeDevise(r[0]), k -> new double[2]);
            cumul[0] += nombre(r[1]);
            cumul[1] += r[2] == null ? 0 : ((Number) r[2]).doubleValue();
        }
        List<MontantDevise> montants = parDevise.entrySet().stream()
                .map(e -> new MontantDevise(e.getKey(), (long) e.getValue()[0], e.getValue()[1]))
                .sorted(java.util.Comparator.comparing((MontantDevise m) -> !Devise.CODE_REFERENCE.equals(m.devise()))
                        .thenComparing(MontantDevise::devise))
                .toList();
        long nombreBc = montants.stream().mapToLong(MontantDevise::nombreBc).sum();

        List<Fournisseur> top = bonCommandeMereRepo.topFournisseurs(valide, debut, fin, PageRequest.of(0, 5)).stream()
                .map(r -> new Fournisseur((String) r[0], codeDevise(r[1]), nombre(r[2]),
                        r[3] == null ? 0 : ((Number) r[3]).doubleValue()))
                .toList();

        long recus = 0;
        long total = 0;
        for (Object[] r : bonCommandeMereRepo.receptionsParStatut(valide, debut, fin)) {
            long n = nombre(r[1]);
            total += n;
            if (r[0] == BonCommandeMere.StatutReception.RECU || r[0] == BonCommandeMere.StatutReception.SOLDE) {
                recus += n;
            }
        }

        Object[] aRecevoir = premiere(bonCommandeMereRepo.compterAReceptionner(
                valide, BonCommandeService.RECEPTIONS_OUVERTES, LocalDateTime.now()));

        return new Indicateurs(
                nombreBc, montants,
                top,
                total, recus, total == 0 ? null : Math.round(recus * 1000.0 / total) / 10.0,
                nombre(aRecevoir[1]),
                stockAlerteService.countAlertes(),
                delaisParEtape(debut, fin));
    }

    /**
     * Pour chaque demande émise sur la période, le temps passé à une étape va de son entrée
     * (dernière action de l'étape précédente, ou date de la demande) à sa sortie (dernière
     * action enregistrée à cette étape). L'étape où la demande se trouve encore est ignorée.
     */
    List<DelaiEtape> delaisParEtape(LocalDateTime debut, LocalDateTime fin) {
        Set<Integer> etapesSuivies = new HashSet<>();
        CIRCUIT.forEach(e -> etapesSuivies.add(e.getStatut()));

        // id demande -> [date de demande, statut actuel] et ses (étape, sortie)
        Map<String, Object[]> demandes = new HashMap<>();
        Map<String, List<Object[]>> sorties = new HashMap<>();
        for (Object[] r : validationDemandeRepo.dernieresActionsParEtape(debut, fin)) {
            String id = (String) r[0];
            int etape = ((Number) r[3]).intValue();
            if (!etapesSuivies.contains(etape) || r[4] == null) continue;
            demandes.putIfAbsent(id, new Object[]{r[1], r[2]});
            sorties.computeIfAbsent(id, k -> new ArrayList<>()).add(new Object[]{etape, r[4]});
        }

        Map<Integer, double[]> cumul = new HashMap<>(); // étape -> [somme des heures, nombre]
        sorties.forEach((id, liste) -> {
            liste.sort(Comparator.comparing(o -> (LocalDateTime) o[1]));
            LocalDateTime entree = (LocalDateTime) demandes.get(id)[0];
            int statutActuel = ((Number) demandes.get(id)[1]).intValue();
            for (Object[] s : liste) {
                int etape = (int) s[0];
                LocalDateTime sortie = (LocalDateTime) s[1];
                if (etape != statutActuel && entree != null) {
                    double heures = Math.max(0, Duration.between(entree, sortie).toMinutes() / 60.0);
                    double[] c = cumul.computeIfAbsent(etape, k -> new double[2]);
                    c[0] += heures;
                    c[1]++;
                }
                entree = sortie;
            }
        });

        double max = cumul.values().stream().filter(c -> c[1] > 0)
                .mapToDouble(c -> c[0] / c[1]).max().orElse(0);
        List<DelaiEtape> delais = new ArrayList<>();
        for (EtapeWorkflow e : CIRCUIT) {
            double[] c = cumul.get(e.getStatut());
            if (c == null || c[1] == 0) continue;
            double moyenneHeures = c[0] / c[1];
            int largeur = max <= 0 ? 0 : (int) Math.round(moyenneHeures * 100 / max);
            delais.add(new DelaiEtape(e.getLibelleValidateur(),
                    Math.round(moyenneHeures / 24 * 10) / 10.0, (long) c[1], largeur));
        }
        return delais;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Utilitaires
    // ════════════════════════════════════════════════════════════════════════

    private static boolean role(Authentication auth, String role) {
        return WorkflowService.hasRole(auth, "ROLE_" + role);
    }

    private static void ajouterSiNonVide(List<Action> actions, String titre, String detail, String icone,
                                         long nombre, Long jours, String url) {
        ajouterSiNonVide(actions, titre, detail, icone, nombre, jours,
                jours != null && jours > JOURS_AVANT_RETARD, url);
    }

    private static void ajouterSiNonVide(List<Action> actions, String titre, String detail, String icone,
                                         long nombre, Long jours, boolean urgent, String url) {
        if (nombre > 0) actions.add(new Action(titre, detail, icone, nombre, jours, urgent, url));
    }

    /** Première ligne d'un agrégat sans GROUP BY (toujours une ligne). */
    private static Object[] premiere(List<Object[]> lignes) {
        return lignes.isEmpty() ? new Object[]{0L, null, null} : lignes.get(0);
    }

    private static long nombre(Object valeur) {
        return valeur == null ? 0 : ((Number) valeur).longValue();
    }

    /** Code de devise d'un agrégat ; un bon sans devise (antérieur) est en ariary. */
    private static String codeDevise(Object valeur) {
        return valeur == null ? Devise.CODE_REFERENCE : valeur.toString().toUpperCase();
    }

    private static Long jours(Object date, LocalDateTime maintenant) {
        return date instanceof LocalDateTime d ? Math.max(0, ChronoUnit.DAYS.between(d, maintenant)) : null;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
