package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.PrixArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrixArticleRepo extends JpaRepository<PrixArticle,Integer> {
    @Query("""
    SELECT p FROM PrixArticle p
    WHERE p.article.codeArticle = :codeArticle
    ORDER BY p.datePrix DESC
    LIMIT 1
""")
    Optional<PrixArticle> findDernierPrixByArticle(@Param("codeArticle") String codeArticle);
}
