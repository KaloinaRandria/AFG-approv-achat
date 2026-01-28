package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepo extends JpaRepository<Article,Integer> {
    @Query("SELECT a FROM Article a WHERE a.codeArticle = :codeArticle")
    Optional<Article> findArticleByCodeArticle(String codeArticle);

    List<Article> findByCodeArticleContainingIgnoreCaseOrDesignationContainingIgnoreCase(String code, String designation);

    @Query("SELECT a FROM Article a WHERE a.designation = :designation")
    Optional<Article> findArticleByDesignation(String designation);

    @Query("""
        SELECT a
        FROM Article a
        LEFT JOIN a.famille f
        LEFT JOIN a.udm u
        WHERE 1=1
          AND (:code = '' OR LOWER(COALESCE(a.codeArticle, '')) LIKE LOWER(CONCAT('%', :code, '%')))
          AND (:designation = '' OR LOWER(COALESCE(a.designation, '')) LIKE LOWER(CONCAT('%', :designation, '%')))
          AND (:famille = '' OR LOWER(COALESCE(f.description, '')) LIKE LOWER(CONCAT('%', :famille, '%')))
          AND (:udm = '' OR LOWER(COALESCE(u.description, '')) LIKE LOWER(CONCAT('%', :udm, '%'))
                          OR LOWER(COALESCE(u.acronyme, '')) LIKE LOWER(CONCAT('%', :udm, '%')))
    """)
    Page<Article> searchMulti(
            @Param("code") String code,
            @Param("designation") String designation,
            @Param("famille") String famille,
            @Param("udm") String udm,
            Pageable pageable
    );
}
