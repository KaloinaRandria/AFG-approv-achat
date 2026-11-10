package afg.achat.afgApprovAchat.service.workflow;

/**
 * Droits de décision d'un utilisateur sur une demande, à son étape actuelle.
 * Source unique utilisée par la fiche (affichage des boutons) et par les actions (contrôle).
 *
 * @param estNPlus1            l'utilisateur est le supérieur hiérarchique direct du demandeur
 * @param estValidateurAssigne l'utilisateur est validateur assigné du demandeur
 */
public record DroitsDecision(boolean estNPlus1, boolean estValidateurAssigne,
                             boolean n1, boolean mg, boolean controleur,
                             boolean dfc, boolean sg, boolean codep) {

    public boolean auMoinsUn() {
        return n1 || mg || controleur || dfc || sg || codep;
    }
}
