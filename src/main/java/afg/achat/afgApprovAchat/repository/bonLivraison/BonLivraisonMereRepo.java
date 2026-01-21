package afg.achat.afgApprovAchat.repository.bonLivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface BonLivraisonMereRepo extends JpaRepository<BonLivraisonMere,String> {
    // Optionnel : recherche (adapte les champs à ton entité)
    Page<BonLivraisonMere> findByIdContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String id, String description, Pageable pageable
    );

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

}
