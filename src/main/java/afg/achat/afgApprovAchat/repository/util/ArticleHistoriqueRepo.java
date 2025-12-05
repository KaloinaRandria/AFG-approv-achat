package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.ArticleHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleHistoriqueRepo extends JpaRepository<ArticleHistorique,Integer> {
    static List<ArticleHistorique> findByArticleCodeArticleOrderByDateModificationDesc(String codeArticle);
    static List<ArticleHistorique> findByArticleIdOrderByDateModificationDesc(Long articleId);
}
