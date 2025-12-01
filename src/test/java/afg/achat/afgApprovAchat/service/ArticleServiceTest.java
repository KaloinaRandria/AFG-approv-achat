package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ArticleServiceTest {
    @Mock
    private ArticleRepo articleRepo;
    @InjectMocks
    private ArticleService articleService;

    @Test
    void testGetAllArticles() {
        // 1️⃣ Préparer des faux articles
        Article a1 = new Article();
        a1.setCodeArticle("A001");

        Article a2 = new Article();
        a2.setCodeArticle("A002");

        List<Article> fakeList = Arrays.asList(a1, a2);

        // 2️⃣ Simuler le comportement du repo
        Mockito.when(articleRepo.findAll()).thenReturn(fakeList);

        // 3️⃣ Appeler la méthode
        Article[] result = articleService.getAllArticles();

        // 4️⃣ Assertions
        assertEquals(2, result.length);
        assertEquals("A001", result[0].getCodeArticle());
        assertEquals("A002", result[1].getCodeArticle());

        // 5️⃣ Vérifier que findAll() a été appelé 1 seule fois
        Mockito.verify(articleRepo, Mockito.times(1)).findAll();
    }


    // ---------------------------------------------------------------
    // Test : getArticleByCodeArticle() -> article trouvé
    // ---------------------------------------------------------------
    @Test
    void testGetArticleByCodeArticle_found() {
        Article fakeArticle = new Article();
        fakeArticle.setCodeArticle("ART123");

        Mockito.when(articleRepo.findArticleByCodeArticle("ART123"))
                .thenReturn(fakeArticle);

        Article result = articleService.getArticleByCodeArticle("ART123");

        assertNotNull(result);
        assertEquals("ART123", result.getCodeArticle());
        System.out.println("Article trouvé : " + result.getCodeArticle());

    }


    // ---------------------------------------------------------------
    // Test : getArticleByCodeArticle() -> article NON trouvé
    // ---------------------------------------------------------------
    @Test
    void testGetArticleByCodeArticle_notFound() {
        Mockito.when(articleRepo.findArticleByCodeArticle("UNKNOWN"))
                .thenReturn(null);

        Article result = articleService.getArticleByCodeArticle("UNKNOWN");

        assertNull(result); // article introuvable
    }
}
