package afg.achat.afgApprovAchat.service.bonCommande;

import java.util.List;

/**
 * Données saisies dans le formulaire d'un bon de commande manuel.
 *
 * @param id          identifiant du bon à modifier ; vide pour une création
 * @param description titre du bon, imprimé au-dessus des lignes (ex. « BRANDING GAB AEROPORT IVATO »)
 * @param tauxRemise  remise en pourcentage du montant HT
 * @param tauxTVA     TVA en pourcentage du montant HT remisé
 */
public record SaisieBonCommande(String id,
                                String fournisseurId,
                                String referenceFournisseur,
                                String dateLivraisonPrevue,
                                String lieuLivraison,
                                String modalitePaiement,
                                String referenceApprobation,
                                String description,
                                Double tauxRemise,
                                Double tauxTVA,
                                List<Ligne> lignes) {

    /**
     * Une ligne du bon : désignation libre, quantité, unité et prix unitaire HT.
     *
     * @param codeArticle    article concerné, facultatif ; il n'est pas imprimé
     * @param demandeFilleId ligne de demande que cette ligne commande, facultative ;
     *                       elle fixe l'article et borne la quantité au reste à commander
     */
    public record Ligne(String designation,
                        String codeArticle,
                        String quantite,
                        String unite,
                        String prixUnitaire,
                        String demandeFilleId) {

        /** Ligne libre, sans lien avec une demande. */
        public Ligne(String designation, String codeArticle, String quantite, String unite, String prixUnitaire) {
            this(designation, codeArticle, quantite, unite, prixUnitaire, null);
        }

        /** Ligne laissée entièrement vide dans le formulaire. */
        public boolean estVide() {
            return vide(designation) && vide(codeArticle) && vide(quantite) && vide(prixUnitaire);
        }

        private static boolean vide(String s) {
            return s == null || s.isBlank();
        }
    }
}
