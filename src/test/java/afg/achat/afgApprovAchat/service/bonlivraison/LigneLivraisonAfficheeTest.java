package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LigneLivraisonAfficheeTest {

    private final Article cahier = article(2, "Cahier");
    private final Article stylo = article(1, "Stylo");

    @Test
    void memeArticleEtMemePrixFusionnes() {
        // BC groupé : 9 cahiers pour une demande, 11 pour une autre → une ligne de 20 sur le BL
        BonLivraisonFille d1 = ligne(cahier, 9, 9, 1500);
        BonLivraisonFille d2 = ligne(cahier, 11, 11, 1500);

        List<LigneLivraisonAffichee> lignes = LigneLivraisonAffichee.regrouper(List.of(d1, d2));

        assertEquals(1, lignes.size());
        LigneLivraisonAffichee l = lignes.get(0);
        assertEquals(20, l.quantiteAttendue());
        assertEquals(20, l.quantiteRecue());
        assertEquals(30000, l.getMontant());
        assertTrue(l.isFusionnee());
        assertEquals(List.of(d1, d2), l.details());
    }

    @Test
    void prixDifferentsRestentSepares() {
        List<LigneLivraisonAffichee> lignes = LigneLivraisonAffichee.regrouper(List.of(
                ligne(cahier, 9, 9, 1500), ligne(cahier, 11, 11, 1600)));
        assertEquals(2, lignes.size());
        assertFalse(lignes.get(0).isFusionnee());
    }

    @Test
    void articlesDifferentsRestentSeparesDansLOrdre() {
        List<LigneLivraisonAffichee> lignes = LigneLivraisonAffichee.regrouper(List.of(
                ligne(cahier, 9, 9, 1500), ligne(stylo, 5, 5, 1500), ligne(cahier, 11, 11, 1500)));
        assertEquals(2, lignes.size());
        assertSame(cahier, lignes.get(0).article());
        assertEquals(20, lignes.get(0).quantiteRecue());
        assertSame(stylo, lignes.get(1).article());
    }

    @Test
    void lignesSansArticleNeSontPasFusionnees() {
        List<LigneLivraisonAffichee> lignes = LigneLivraisonAffichee.regrouper(List.of(
                ligne(null, 1, 1, 100), ligne(null, 2, 2, 100)));
        assertEquals(2, lignes.size());
    }

    private static Article article(int id, String designation) {
        Article a = new Article();
        a.setId(id);
        a.setDesignation(designation);
        return a;
    }

    private static BonLivraisonFille ligne(Article article, double attendue, double recue, double prix) {
        BonLivraisonFille l = new BonLivraisonFille();
        l.setArticle(article);
        l.setQuantiteDemande(String.valueOf(attendue));
        l.setQuantiteRecu(String.valueOf(recue));
        l.setPrixUnitaire(String.valueOf(prix));
        return l;
    }
}
