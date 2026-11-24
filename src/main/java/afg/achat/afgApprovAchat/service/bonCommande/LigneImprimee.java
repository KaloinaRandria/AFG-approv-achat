package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ligne du bon de commande telle qu'elle est imprimée pour le fournisseur.
 *
 * <p>En base, un bon garde une ligne par ligne de demande (pour suivre ce qui est commandé
 * pour chaque demande). À l'impression, les lignes identiques pour le fournisseur — même
 * article, même désignation, même unité et même prix unitaire — sont fusionnées : leurs
 * quantités et leurs montants s'additionnent. Des prix différents donnent des lignes séparées.</p>
 *
 * @param details lignes du bon regroupées ici, dans leur ordre de saisie
 */
public record LigneImprimee(String designation,
                            String unite,
                            Article article,
                            double quantite,
                            double prixUnitaireHT,
                            double montantHT,
                            List<BonCommandeFille> details) {

    /** Plusieurs lignes du bon ont été fusionnées dans cette ligne. */
    public boolean isFusionnee() {
        return details.size() > 1;
    }

    /**
     * Regroupe les lignes du bon pour l'impression, dans l'ordre de leur première apparition.
     * Les montants totaux sont inchangés : seule la présentation change.
     */
    public static List<LigneImprimee> regrouper(List<BonCommandeFille> lignes) {
        Map<Cle, List<BonCommandeFille>> groupes = new LinkedHashMap<>();
        for (BonCommandeFille ligne : lignes) {
            groupes.computeIfAbsent(Cle.de(ligne), k -> new ArrayList<>()).add(ligne);
        }

        List<LigneImprimee> resultat = new ArrayList<>();
        for (List<BonCommandeFille> groupe : groupes.values()) {
            BonCommandeFille premiere = groupe.get(0);
            resultat.add(new LigneImprimee(
                    premiere.getDesignationAffichee(),
                    premiere.getUniteAffichee(),
                    premiere.getArticle(),
                    groupe.stream().mapToDouble(BonCommandeFille::getQuantiteCommandee).sum(),
                    premiere.getPrixUnitaireHT(),
                    groupe.stream().mapToDouble(BonCommandeFille::getMontantHT).sum(),
                    List.copyOf(groupe)));
        }
        return resultat;
    }

    /** Ce que voit le fournisseur : deux lignes avec la même clé sont indiscernables pour lui. */
    private record Cle(Integer articleId, String designation, String unite, double prix) {
        static Cle de(BonCommandeFille ligne) {
            return new Cle(ligne.getArticle() != null ? ligne.getArticle().getId() : null,
                    normaliser(ligne.getDesignationAffichee()),
                    normaliser(ligne.getUniteAffichee()),
                    ligne.getPrixUnitaireHT());
        }

        private static String normaliser(String texte) {
            return Objects.toString(texte, "").trim().replaceAll("\\s+", " ").toLowerCase();
        }
    }
}
