package afg.achat.afgApprovAchat.DTO;

/**
 * Demandes d'un service sur une période : nombre et montants en ariary (contre-valeurs
 * enregistrées), validés d'une part, encore dans le circuit d'autre part.
 */
public record ServiceDemandeDTO(String acronymeService, Long nbDemande, Double montantValide, Double montantEnCours) {

    /** Nombre seul (sans montants). */
    public ServiceDemandeDTO(String acronymeService, Long nbDemande) {
        this(acronymeService, nbDemande, 0.0, 0.0);
    }

    public double getMontantTotal() {
        return (montantValide == null ? 0 : montantValide) + (montantEnCours == null ? 0 : montantEnCours);
    }
}
