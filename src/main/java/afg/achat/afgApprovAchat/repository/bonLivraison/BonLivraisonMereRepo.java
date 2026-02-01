package afg.achat.afgApprovAchat.repository.bonLivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BonLivraisonMereRepo extends JpaRepository<BonLivraisonMere,String> {

    @Query("""
        select bl from BonLivraisonMere bl
        left join bl.fournisseur f
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

    @Query("""
    select bl from BonLivraisonMere bl
    left join bl.fournisseur f
    left join bl.devise d
    where bl.dateReception between :dateFrom and :dateTo

      and (:num = '' or lower(bl.id) like lower(concat('%', :num, '%')))

      and (
            :fournisseur = ''
            or lower(coalesce(f.nom, '')) like lower(concat('%', :fournisseur, '%'))
      )

      and (
            :devise = ''
            or lower(coalesce(d.designation, '')) like lower(concat('%', :devise, '%'))
            or lower(coalesce(d.acronyme, '')) like lower(concat('%', :devise, '%'))
      )
""")
    Page<BonLivraisonMere> searchMulti(
            @Param("num") String num,
            @Param("fournisseur") String fournisseur,
            @Param("devise") String devise,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );
    @Query("SELECT bl FROM BonLivraisonMere  bl WHERE bl.idFacture = :idFacture")
    BonLivraisonMere findByIdFacture(String idFacture);

    boolean existsByIdFacture(String idFacture);


}
