package afg.achat.afgApprovAchat.service.workflow;

import org.springframework.web.multipart.MultipartFile;

/**
 * Données saisies avec une décision sur la fiche d'une demande.
 * Seuls certains champs servent selon l'étape (MG : type, priorité, mode ;
 * contrôleur : ligne budgétaire et commentaire).
 */
public record DecisionDemande(String commentaire,
                              MultipartFile[] piecesJointes,
                              String typeDemande,
                              String priorite,
                              String modeTraitement,
                              String ligneBudgetaire,
                              String commentaireControleur) {

    public String commentaireNettoye() {
        return commentaire == null ? "" : commentaire.trim();
    }
}
