package afg.achat.afgApprovAchat.service.workflow;

/**
 * Refus métier d'une action du workflow (droit manquant, champ obligatoire absent…).
 * Le message est destiné à l'utilisateur. Levée dans une transaction, elle annule
 * toutes les écritures déjà faites.
 */
public class WorkflowException extends RuntimeException {
    public WorkflowException(String message) {
        super(message);
    }
}
