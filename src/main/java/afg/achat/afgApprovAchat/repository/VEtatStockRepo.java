package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.VEtatStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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
            ORDER BY v_etat_stock.code_article DESC
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
      WHERE v_etat_stock.code_article IN (:codes)
        AND (
            CAST(stock_disponible AS DECIMAL(18,2)) <= 0
            OR CAST(stock_disponible AS DECIMAL(18,2)) <= CAST(seuil_min AS DECIMAL(18,2))
        )
  """,
            nativeQuery = true
    )
    List<VEtatStock> findAlertesForCodes(@Param("codes") java.util.List<String> codes);

    @Query(
            value = """
          SELECT *
          FROM v_etat_stock
          WHERE CAST(stock_disponible AS numeric) <= 0
             OR CAST(stock_disponible AS numeric) <= CAST(seuil_min AS numeric)
          ORDER BY code_article DESC
        """,
            nativeQuery = true
    )
    List<VEtatStock> findAlertesAll();

    @Query(value = """
SELECT *
FROM v_etat_stock v
WHERE 1=1
  AND (:code = '' OR lower(coalesce(v.code_article,'')) LIKE lower(concat('%', :code, '%')))
  AND (:designation = '' OR lower(coalesce(v.designation,'')) LIKE lower(concat('%', :designation, '%')))
  AND (:udm = '' OR lower(coalesce(v.unite_de_mesure,'')) LIKE lower(concat('%', :udm, '%'))
               OR lower(coalesce(v.desc_udm,'')) LIKE lower(concat('%', :udm, '%')))

  AND (
        :etat = '' OR
        (:etat = 'RUPTURE' AND CAST(v.stock_disponible AS numeric) <= 0)
        OR
        (:etat = 'SEUIL'
            AND CAST(v.stock_disponible AS numeric) > 0
            AND CAST(v.stock_disponible AS numeric) <= CAST(v.seuil_min AS numeric)
        )
        OR
        (:etat = 'NORMAL'
            AND CAST(v.stock_disponible AS numeric) > CAST(v.seuil_min AS numeric)
        )
  )
ORDER BY v.code_article ASC
""",
            countQuery = """
SELECT COUNT(*)
FROM v_etat_stock v
WHERE 1=1
  AND (:code = '' OR lower(coalesce(v.code_article,'')) LIKE lower(concat('%', :code, '%')))
  AND (:designation = '' OR lower(coalesce(v.designation,'')) LIKE lower(concat('%', :designation, '%')))
  AND (:udm = '' OR lower(coalesce(v.unite_de_mesure,'')) LIKE lower(concat('%', :udm, '%'))
               OR lower(coalesce(v.desc_udm,'')) LIKE lower(concat('%', :udm, '%')))

  AND (
        :etat = '' OR
        (:etat = 'RUPTURE' AND CAST(v.stock_disponible AS numeric) <= 0)
        OR
        (:etat = 'SEUIL'
            AND CAST(v.stock_disponible AS numeric) > 0
            AND CAST(v.stock_disponible AS numeric) <= CAST(v.seuil_min AS numeric)
        )
        OR
        (:etat = 'NORMAL'
            AND CAST(v.stock_disponible AS numeric) > CAST(v.seuil_min AS numeric)
        )
  )
""",
            nativeQuery = true)
    Page<VEtatStock> searchMulti(
            @Param("code") String code,
            @Param("designation") String designation,
            @Param("udm") String udm,
            @Param("etat") String etat,
            Pageable pageable
    );

}
