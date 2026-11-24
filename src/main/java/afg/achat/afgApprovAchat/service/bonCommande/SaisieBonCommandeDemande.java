package afg.achat.afgApprovAchat.service.bonCommande;

import java.util.Map;

/**
 * Données saisies par les Moyens Généraux pour compléter un bon de commande issu d'une demande.
 * Les quantités et les prix ne sont pas saisis : ils viennent des lignes de la demande.
 *
 * @param designationsFournisseur désignation fournisseur par identifiant de ligne de bon de commande
 * @param tauxRemise              remise en pourcentage du montant HT
 * @param tauxTVA                 TVA en pourcentage du montant HT remisé
 */
public record SaisieBonCommandeDemande(String id,
                                       String numero,
                                       String dateLivraisonPrevue,
                                       String fournisseurId,
                                       String referenceFournisseur,
                                       String lieuLivraison,
                                       Double tauxRemise,
                                       Double tauxTVA,
                                       Responsable responsable,
                                       Adresse adresse,
                                       Map<Integer, String> designationsFournisseur) {

    /**
     * Personne de contact du fournisseur.
     *
     * @param action "update" (corriger la personne affectée), "new" (affecter une autre
     *               personne) ou vide (garder la personne choisie, ou en créer une si aucune)
     */
    public record Responsable(String action, String responsableFournisseurId,
                              String nom, String prenom, String contact) {
        boolean nouveauxChampsRenseignes() {
            return nom != null && !nom.isBlank() && prenom != null && !prenom.isBlank();
        }
    }

    /**
     * Adresse du fournisseur.
     *
     * @param action "update", "new" ou vide (mêmes règles que pour le responsable)
     */
    public record Adresse(String action, String adresseFournisseurId, String libelle) {
        boolean libelleRenseigne() {
            return libelle != null && !libelle.isBlank();
        }
    }
}
