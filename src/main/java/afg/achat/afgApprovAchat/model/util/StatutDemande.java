package afg.achat.afgApprovAchat.model.util;

public final class StatutDemande {

    // ----- Etat initial -----
    public static final int CREE = 1;

    // ----- Workflow normal -----
    public static final int VALIDATION_N1 = 11;   // Manager N+1
    public static final int VALIDATION_N2 = 12;   // Moyens Généraux (validation normale)
    public static final int VALIDATION_N3 = 13;   // Contrôleur de gestion
    public static final int VALIDATION_N4 = 14;   // DFC
    public static final int VALIDE = 15;          // Validation finale (SG)



    // ----- Workflow CODEP -----
    public static final int DECISION_CODEP = 20;  // En attente décision CODEP

    // ----- Etat final négatif -----
    public static final int REFUSE = -1;
}

 