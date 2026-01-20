package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.VEtatStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VEtatStockRepo extends JpaRepository<VEtatStock, Integer> {

    @Query("SELECT v FROM VEtatStock v WHERE v.codeArticle = :codeArticle")
    VEtatStock findByCodeArticle(String codeArticle);

    // ✅ Pagination + recherche globale (pour la liste)
    Page<VEtatStock> findByCodeArticleContainingIgnoreCaseOrDesignationContainingIgnoreCase(
            String code,
            String designation,
            Pageable pageable
    );

    @Query(
            value = """
            SELECT *
            FROM v_etat_stock
            WHERE CAST(stock_disponible AS numeric) <= 0
               OR CAST(stock_disponible AS numeric) <= CAST(seuil_min AS numeric)
            ORDER BY code_article DESC
        """,
            countQuery = """
            SELECT COUNT(*)
            FROM v_etat_stock
            WHERE CAST(stock_disponible AS numeric) <= 0
               OR CAST(stock_disponible AS numeric) <= CAST(seuil_min AS numeric)
        """,
            nativeQuery = true
    )
    Page<VEtatStock> findAlertesPage(Pageable pageable);



    @Query(
            value = """
      SELECT COUNT(*)
      FROM v_etat_stock
      WHERE CAST(stock_disponible AS DECIMAL(18,2)) <= 0
         OR CAST(stock_disponible AS DECIMAL(18,2)) <= CAST(seuil_min AS DECIMAL(18,2))
  """,
            nativeQuery = true
    )
    long countAlertes();

    @Query(
            value = """
      SELECT *
      FROM v_etat_stock
      WHERE code_article IN (:codes)
        AND (
            CAST(stock_disponible AS DECIMAL(18,2)) <= 0
            OR CAST(stock_disponible AS DECIMAL(18,2)) <= CAST(seuil_min AS DECIMAL(18,2))
        )
  """,
            nativeQuery = true
    )
    java.util.List<VEtatStock> findAlertesForCodes(@Param("codes") java.util.List<String> codes);

}
