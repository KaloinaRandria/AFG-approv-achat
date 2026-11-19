package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ligne du bon de livraison telle qu'elle est affichée et imprimée.
 *
 * <p>Un bon de commande groupé garde une ligne par ligne de demande (ex. 9 cahiers pour une
 * demande, 11 pour une autre) ; sa réception crée donc autant de lignes de BL. Le fournisseur
 * n'en a livré qu'une (20 cahiers) : à l'affichage, les lignes de même article et même prix
 * unitaire sont fusionnées, comme sur le bon de commande imprimé. Les données gardent le
 * détail par demande ({@link #details()}).</p>
 */
public record LigneLivraisonAffichee(Article article,
                                     double quantiteAttendue,
                                     double quantiteRecue,
                                     double prixUnitaire,
                                     List<BonLivraisonFille> details) {

    public double getMontant() {
        return quantiteRecue * prixUnitaire;
    }

    /** Plusieurs lignes du BL ont été fusionnées dans cette ligne. */
    public boolean isFusionnee() {
        return details.size() > 1;
    }

    /** Désignation du bon de commande, quand elle diffère de celle de l'article ; sinon {@code null}. */
    public String getDesignationBonCommande() {
        BonLivraisonFille premiere = details.get(0);
        if (premiere.getBonCommandeFille() == null || article == null) return null;
        String designation = premiere.getBonCommandeFille().getDesignationAffichee();
        return Objects.equals(designation, article.getDesignation()) ? null : designation;
    }

    /** Regroupe les lignes du BL dans l'ordre de leur première apparition ; le total est inchangé. */
    public static List<LigneLivraisonAffichee> regrouper(List<BonLivraisonFille> lignes) {
        Map<Cle, List<BonLivraisonFille>> groupes = new LinkedHashMap<>();
        for (BonLivraisonFille ligne : lignes) {
            groupes.computeIfAbsent(Cle.de(ligne), k -> new ArrayList<>()).add(ligne);
        }
        List<LigneLivraisonAffichee> resultat = new ArrayList<>();
        for (List<BonLivraisonFille> groupe : groupes.values()) {
            BonLivraisonFille premiere = groupe.get(0);
            resultat.add(new LigneLivraisonAffichee(
                    premiere.getArticle(),
                    groupe.stream().mapToDouble(BonLivraisonFille::getQuantiteDemande).sum(),
                    groupe.stream().mapToDouble(BonLivraisonFille::getQuantiteRecu).sum(),
                    premiere.getPrixUnitaire(),
                    List.copyOf(groupe)));
        }
        return resultat;
    }

    /** Même article et même prix : indiscernables pour le fournisseur. Sans article, la ligne reste seule. */
    private record Cle(Object article, double prix) {
        static Cle de(BonLivraisonFille ligne) {
            Object article = ligne.getArticle() != null ? (Object) ligne.getArticle().getId() : new Object();
            return new Cle(article, ligne.getPrixUnitaire());
        }
    }
}
