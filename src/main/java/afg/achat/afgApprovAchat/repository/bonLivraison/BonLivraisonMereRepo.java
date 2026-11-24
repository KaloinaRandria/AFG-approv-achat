package afg.achat.afgApprovAchat.repository.bonLivraison;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BonLivraisonMereRepo extends JpaRepository<BonLivraisonMere,String> {

    @Query("""
        select bl from BonLivraisonMere bl
        join bl.fournisseur f
        left join bl.devise d
        where (
            :q = '' or
            lower(bl.id) like lower(concat('%', :q, '%')) or
            lower(coalesce(bl.description, '')) like lower(concat('%', :q, '%')) or
            lower(coalesce(f.nom, '')) like lower(concat('%', :q, '%')) or
            lower(coalesce(d.designation, '')) like lower(concat('%', :q, '%')) or
            lower(coalesce(d.acronyme, '')) like lower(concat('%', :q, '%'))
        )
        and bl.dateReception >= coalesce(:dateFrom, bl.dateReception)
        and bl.dateReception <= coalesce(:dateTo, bl.dateReception)
    """)
    Page<BonLivraisonMere> search(
            @Param("q") String q,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    /**
     * Recherche multicritère de la liste des bons de livraison. Chaque filtre texte vide est
     * ignoré ; la période porte sur la date de réception.
     *
     * @param numero      identifiant du BL ou observation (n° de BL du fournisseur)
     * @param bonCommande numéro ou identifiant du bon de commande réceptionné
     * @param article     code ou désignation d'un article reçu
     * @param deviseId    devise ; 0 = toutes
     * @param origine     {@code BC} : réceptions de bons de commande, {@code HORS_BC} : saisies sans bon, '' : tous
     */
    @Query(value = """
        SELECT bl FROM BonLivraisonMere bl
        LEFT JOIN FETCH bl.fournisseur f
        LEFT JOIN FETCH bl.devise d
        LEFT JOIN FETCH bl.bonCommandeMere bc
        WHERE bl.dateReception BETWEEN :du AND :au
          AND (:numero = '' OR LOWER(bl.id) LIKE LOWER(CONCAT('%', :numero, '%'))
                            OR LOWER(COALESCE(bl.description, '')) LIKE LOWER(CONCAT('%', :numero, '%')))
          AND (:fournisseur = '' OR LOWER(COALESCE(f.nom, '')) LIKE LOWER(CONCAT('%', :fournisseur, '%')))
          AND (:bonCommande = '' OR LOWER(COALESCE(bc.numero, '')) LIKE LOWER(CONCAT('%', :bonCommande, '%'))
                                 OR LOWER(COALESCE(bc.id, '')) LIKE LOWER(CONCAT('%', :bonCommande, '%')))
          AND (:article = '' OR EXISTS (SELECT 1 FROM BonLivraisonFille l
                          LEFT JOIN l.article a
                          WHERE l.bonLivraisonMere = bl
                            AND (LOWER(COALESCE(a.codeArticle, '')) LIKE LOWER(CONCAT('%', :article, '%'))
                                 OR LOWER(COALESCE(a.designation, '')) LIKE LOWER(CONCAT('%', :article, '%')))))
          AND (:deviseId = 0 OR d.id = :deviseId)
          AND (:origine = '' OR (:origine = 'BC' AND bl.bonCommandeMere IS NOT NULL)
                             OR (:origine = 'HORS_BC' AND bl.bonCommandeMere IS NULL))
        ORDER BY bl.dateReception DESC, bl.id DESC
        """,
        countQuery = """
        SELECT COUNT(bl) FROM BonLivraisonMere bl
        LEFT JOIN bl.fournisseur f
        LEFT JOIN bl.devise d
        LEFT JOIN bl.bonCommandeMere bc
        WHERE bl.dateReception BETWEEN :du AND :au
          AND (:numero = '' OR LOWER(bl.id) LIKE LOWER(CONCAT('%', :numero, '%'))
                            OR LOWER(COALESCE(bl.description, '')) LIKE LOWER(CONCAT('%', :numero, '%')))
          AND (:fournisseur = '' OR LOWER(COALESCE(f.nom, '')) LIKE LOWER(CONCAT('%', :fournisseur, '%')))
          AND (:bonCommande = '' OR LOWER(COALESCE(bc.numero, '')) LIKE LOWER(CONCAT('%', :bonCommande, '%'))
                                 OR LOWER(COALESCE(bc.id, '')) LIKE LOWER(CONCAT('%', :bonCommande, '%')))
          AND (:article = '' OR EXISTS (SELECT 1 FROM BonLivraisonFille l
                          LEFT JOIN l.article a
                          WHERE l.bonLivraisonMere = bl
                            AND (LOWER(COALESCE(a.codeArticle, '')) LIKE LOWER(CONCAT('%', :article, '%'))
                                 OR LOWER(COALESCE(a.designation, '')) LIKE LOWER(CONCAT('%', :article, '%')))))
          AND (:deviseId = 0 OR d.id = :deviseId)
          AND (:origine = '' OR (:origine = 'BC' AND bl.bonCommandeMere IS NOT NULL)
                             OR (:origine = 'HORS_BC' AND bl.bonCommandeMere IS NULL))
        """)
    Page<BonLivraisonMere> rechercher(@Param("du") LocalDateTime du,
                                      @Param("au") LocalDateTime au,
                                      @Param("numero") String numero,
                                      @Param("fournisseur") String fournisseur,
                                      @Param("bonCommande") String bonCommande,
                                      @Param("article") String article,
                                      @Param("deviseId") int deviseId,
                                      @Param("origine") String origine,
                                      Pageable pageable);
    @Query("SELECT bl FROM BonLivraisonMere  bl WHERE bl.idFacture = :idFacture")
    BonLivraisonMere findByIdFacture(String idFacture);

    boolean existsByIdFacture(String idFacture);

    /** Bons de livraison qui réceptionnent un bon de commande, du plus récent au plus ancien. */
    List<BonLivraisonMere> findByBonCommandeMereOrderByDateReceptionDesc(BonCommandeMere bonCommandeMere);


}
