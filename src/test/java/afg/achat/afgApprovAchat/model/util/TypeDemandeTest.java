package afg.achat.afgApprovAchat.model.util;

import afg.achat.afgApprovAchat.model.Article;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeDemandeTest {

    @Test
    void seuleLaDemandeDeMaterielSeCreeEncore() {
        assertTrue(TypeDemande.FOURNITURE.isCreable());
        assertFalse(TypeDemande.STANDARD.isCreable());
    }

    @Test
    void seuleLaDemandeDeMaterielUtiliseLeStock() {
        assertTrue(TypeDemande.FOURNITURE.isArticlesEnStock());
        assertFalse(TypeDemande.STANDARD.isArticlesEnStock());
    }

    @Test
    void lectureDUnTypeSaisi() {
        assertEquals(TypeDemande.FOURNITURE, TypeDemande.parse(" fourniture "));
        assertNull(TypeDemande.parse(""));
        assertNull(TypeDemande.parse(null));
        assertThrows(IllegalArgumentException.class, () -> TypeDemande.parse("INCONNU"));
        assertEquals(TypeDemande.STANDARD, TypeDemande.ouStandard(null));
    }

    @Test
    void unArticleEstActifSaufSIlEstDesactive() {
        Article article = new Article();
        assertTrue(article.estActif());

        article.setActif(null); // article antérieur à l'ajout du statut
        assertTrue(article.estActif());

        article.setActif(false);
        assertFalse(article.estActif());
    }
}
