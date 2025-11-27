package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepo extends JpaRepository<Article,Integer> {
}
