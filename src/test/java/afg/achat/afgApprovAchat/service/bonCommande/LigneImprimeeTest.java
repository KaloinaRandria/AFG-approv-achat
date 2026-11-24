package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LigneImprimeeTest {

    private final Article styloBleu = article(1, "Stylo bleu");
    private final Article cahier = article(2, "Cahier");

    @Test
    void memeArticleEtMemePrixFusionnes() {
        // 5 stylos pour une demande, 3 pour une autre : une seule ligne de 8 pour le fournisseur
        BonCommandeFille d1 = ligne(styloBleu, 5, 500);
        BonCommandeFille d2 = ligne(styloBleu, 3, 500);

        List<LigneImprimee> imprimees = LigneImprimee.regrouper(List.of(d1, d2));

        assertEquals(1, imprimees.size());
        LigneImprimee l = imprimees.get(0);
        assertEquals(8, l.quantite());
        assertEquals(500, l.prixUnitaireHT());
        assertEquals(4000, l.montantHT());
        assertTrue(l.isFusionnee());
        assertEquals(List.of(d1, d2), l.details());
    }

    @Test
    void prixDifferentsRestentSepares() {
        List<LigneImprimee> imprimees = LigneImprimee.regrouper(List.of(
                ligne(styloBleu, 5, 500), ligne(styloBleu, 3, 550)));
        assertEquals(2, imprimees.size());
        assertFalse(imprimees.get(0).isFusionnee());
    }

    @Test
    void ordreDePremiereApparitionEtTotalInchange() {
        List<BonCommandeFille> lignes = List.of(
                ligne(cahier, 2, 1000), ligne(styloBleu, 5, 500), ligne(cahier, 1, 1000));

        List<LigneImprimee> imprimees = LigneImprimee.regrouper(lignes);

        assertEquals(2, imprimees.size());
        assertSame(cahier, imprimees.get(0).article());
        assertEquals(3, imprimees.get(0).quantite());
        assertSame(styloBleu, imprimees.get(1).article());
        assertEquals(lignes.stream().mapToDouble(BonCommandeFille::getMontantHT).sum(),
                imprimees.stream().mapToDouble(LigneImprimee::montantHT).sum());
    }

    @Test
    void designationsDifferentesRestentSeparees() {
        BonCommandeFille a = ligne(styloBleu, 5, 500);
        BonCommandeFille b = ligne(styloBleu, 3, 500);
        b.setDesignation("Stylo bleu pointe fine");
        assertEquals(2, LigneImprimee.regrouper(List.of(a, b)).size());
    }

    @Test
    void lignesLibresIdentiquesFusionneesMalgreEspacesEtCasse() {
        BonCommandeFille a = ligne(null, 1, 200);
        a.setDesignation("Frais de livraison");
        BonCommandeFille b = ligne(null, 1, 200);
        b.setDesignation("  frais   de livraison ");
        assertEquals(1, LigneImprimee.regrouper(List.of(a, b)).size());
    }

    private static BonCommandeFille ligne(Article article, double quantite, double prix) {
        BonCommandeFille l = new BonCommandeFille();
        l.setArticle(article);
        l.setDesignation(article != null ? article.getDesignation() : "");
        l.setUnite("Unité");
        l.setQuantiteCommandee(quantite);
        l.setPrixUnitaireHT(prix);
        l.setMontantHT(quantite * prix);
        return l;
    }

    private static Article article(int id, String designation) {
        Article a = new Article();
        a.setId(id);
        a.setDesignation(designation);
        return a;
    }
}
