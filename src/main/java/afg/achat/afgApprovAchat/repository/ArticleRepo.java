package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepo extends JpaRepository<Article,Integer> {
    @Query("SELECT a FROM Article a WHERE a.codeArticle = :codeArticle")
    Optional<Article> findArticleByCodeArticle(String codeArticle);

    List<Article> findByCodeArticleContainingIgnoreCaseOrDesignationContainingIgnoreCase(String code, String designation);
}
