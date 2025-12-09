package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.ArticleHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleHistoriqueRepo extends JpaRepository<ArticleHistorique,Integer> {
    @Query("SELECT a FROM ArticleHistorique a WHERE a.codeArticle = :codeArticle")
    ArticleHistorique[] findArticleHistoriquesByCodeArticle(String codeArticle);
}
