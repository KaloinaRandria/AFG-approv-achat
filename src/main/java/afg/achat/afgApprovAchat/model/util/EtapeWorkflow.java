package afg.achat.afgApprovAchat.model.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Description unique des étapes du workflow de validation d'une demande.
 *
 * <p>Chaque étape correspond à un statut de {@link StatutDemande} et porte :
 * le libellé « en attente », le validateur qui agit à cette étape, le rôle requis,
 * l'étape suivante en cas d'approbation, le préfixe des pièces jointes et
 * les éléments d'affichage (badges, icône).</p>
 *
 * <p>Circuit normal : CREE → N1 (MG) → N2 (Contrôleur) → N3 (DFC) → N4 (SG) → VALIDE.<br>
 * Circuit CODEP : … → N1 (MG) → DECISION_CODEP → N2 → … (les MG envoient au CODEP).</p>
 */
public enum EtapeWorkflow {

    //              statut                        en attente                         validateur à cette étape   rôle requis        préfixe PJ        badge liste         badge fiche      icône
    CREE          (StatutDemande.CREE,           "En attente N+1",                 "N+1",                     null,              "PJ_N1",          "badge-grey",       "badge-wait",    "fa-user-clock"),
    VALIDATION_N1 (StatutDemande.VALIDATION_N1,  "En attente M.G.",                "Moyens Généraux",         "MOYENS_GENERAUX", "PJ_MG",          "badge-blue",       "badge-info",    "fa-clipboard-check"),
    VALIDATION_N2 (StatutDemande.VALIDATION_N2,  "En attente Contrôle de gestion", "Contrôleur de gestion",   "CONTROLEUR",      "PJ_CONTROLEUR",  "badge-light-blue", "badge-purple",  "fa-coins"),
    VALIDATION_N3 (StatutDemande.VALIDATION_N3,  "En attente D.F.C.",              "D.F.C.",                  "DFC",             "PJ_DFC",         "badge-purple",     "badge-warning", "fa-gavel"),
    VALIDATION_N4 (StatutDemande.VALIDATION_N4,  "En attente S.G.",                "S.G.",                    "SG",              "PJ_SG",          "badge-green-soft", "badge-teal",    "fa-stamp"),
    DECISION_CODEP(StatutDemande.DECISION_CODEP, "En attente CODEP",               "Comité de dépense",       "MOYENS_GENERAUX", "PV_CODEP",       "badge-orange",     "badge-codep",   "fa-landmark"),
    VALIDE        (StatutDemande.VALIDE,         "VALIDÉE",                        null,                      null,              null,             "badge-green",      "badge-success", "fa-check-circle"),
    REFUSE        (StatutDemande.REFUSE,         "REFUSÉE",                        null,                      null,              null,             "badge-red",        "badge-danger",  "fa-times-circle");

    private final int statut;
    private final String libelleAttente;
    private final String libelleValidateur;
    private final String roleValidateur;
    private final String prefixePieceJointe;
    private final String badgeListe;
    private final String badgeFiche;
    private final String icone;

    EtapeWorkflow(int statut, String libelleAttente, String libelleValidateur, String roleValidateur,
                  String prefixePieceJointe, String badgeListe, String badgeFiche, String icone) {
        this.statut = statut;
        this.libelleAttente = libelleAttente;
        this.libelleValidateur = libelleValidateur;
        this.roleValidateur = roleValidateur;
        this.prefixePieceJointe = prefixePieceJointe;
        this.badgeListe = badgeListe;
        this.badgeFiche = badgeFiche;
        this.icone = icone;
    }

    public int getStatut()                { return statut; }
    public String getLibelleAttente()     { return libelleAttente; }
    public String getLibelleValidateur()  { return libelleValidateur; }
    /** Rôle (sans préfixe ROLE_) qui décide à cette étape ; {@code null} pour l'étape N+1 (hiérarchie). */
    public String getRoleValidateur()     { return roleValidateur; }
    public String getPrefixePieceJointe() { return prefixePieceJointe; }

    public boolean estTerminale() {
        return this == VALIDE || this == REFUSE;
    }

    /** Étape atteinte quand la demande est approuvée à cette étape ; {@code null} si terminale. */
    public EtapeWorkflow suivante() {
        return switch (this) {
            case CREE           -> VALIDATION_N1;
            case VALIDATION_N1  -> VALIDATION_N2;   // l'envoi au CODEP est une action distincte
            case DECISION_CODEP -> VALIDATION_N2;
            case VALIDATION_N2  -> VALIDATION_N3;
            case VALIDATION_N3  -> VALIDATION_N4;
            case VALIDATION_N4  -> VALIDE;
            case VALIDE, REFUSE -> null;
        };
    }

    public static EtapeWorkflow fromStatut(int statut) {
        for (EtapeWorkflow e : values()) {
            if (e.statut == statut) return e;
        }
        throw new IllegalArgumentException("Statut de demande inconnu : " + statut);
    }

    // ── Données d'affichage (clé = statut) ──────────────────────────────────

    public static Map<Integer, String> libellesAttente() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (EtapeWorkflow e : values()) m.put(e.statut, e.libelleAttente);
        return m;
    }

    /** Validateur qui agit à chaque étape (libellés de l'historique). */
    public static Map<Integer, String> libellesValidateur() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (EtapeWorkflow e : values()) {
            if (e.libelleValidateur != null) m.put(e.statut, e.libelleValidateur);
        }
        return m;
    }

    public static Map<Integer, String> badgesListe() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (EtapeWorkflow e : values()) m.put(e.statut, e.badgeListe);
        return m;
    }

    public static Map<Integer, String> badgesFiche() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (EtapeWorkflow e : values()) m.put(e.statut, e.badgeFiche);
        return m;
    }

    public static Map<Integer, String> icones() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (EtapeWorkflow e : values()) m.put(e.statut, e.icone);
        return m;
    }

    /** Libellé de l'étape franchie pour atteindre chaque statut (frise de la fiche). */
    public static Map<Integer, String> libellesEtapeFranchie(boolean viaCodep) {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(StatutDemande.CREE,           "Création");
        m.put(StatutDemande.VALIDATION_N1,  "N+1");
        m.put(StatutDemande.VALIDATION_N2,  "Moyens Généraux");
        m.put(StatutDemande.VALIDATION_N3,  "Contrôleur de gestion");
        m.put(StatutDemande.VALIDATION_N4,  "D.F.C.");
        m.put(StatutDemande.DECISION_CODEP, "Comité de dépense");
        m.put(StatutDemande.REFUSE,         "Refus");
        m.put(StatutDemande.VALIDE,         viaCodep ? "Comité de dépense" : "S.G.");
        return m;
    }

    // ── Circuit ─────────────────────────────────────────────────────────────

    /** Étapes parcourues, dans l'ordre, selon que la demande passe ou non par le CODEP. */
    public static List<EtapeWorkflow> circuit(boolean viaCodep) {
        return viaCodep
                ? List.of(CREE, VALIDATION_N1, DECISION_CODEP, VALIDATION_N2, VALIDATION_N3, VALIDATION_N4, VALIDE)
                : List.of(CREE, VALIDATION_N1, VALIDATION_N2, VALIDATION_N3, VALIDATION_N4, VALIDE);
    }

    /** Libellés de la frise de progression (un par position du circuit). */
    public static List<String> libellesCircuit(boolean viaCodep) {
        return viaCodep
                ? List.of("Demande créée", "Supérieur hiérarchique (N+1)", "Moyens Généraux",
                          "Comité de dépense", "Contrôleur de gestion", "D.F.C.", "S.G.")
                : List.of("Demande créée", "Supérieur hiérarchique (N+1)", "Moyens Généraux",
                          "Contrôleur de gestion", "D.F.C.", "S.G.");
    }

    /** Position (1-based) du statut dans le circuit ; -1 si refusée, 1 si inconnu. */
    public static int numeroDansCircuit(int statut, boolean viaCodep) {
        if (statut == StatutDemande.REFUSE) return -1;
        int index = Arrays.stream(values()).anyMatch(e -> e.statut == statut)
                ? circuit(viaCodep).indexOf(fromStatut(statut))
                : -1;
        return index < 0 ? 1 : index + 1;
    }
}
