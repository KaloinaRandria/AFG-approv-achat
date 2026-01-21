package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DemandeMereRepo extends JpaRepository<DemandeMere, String> {

    @Query("""
        select dm from DemandeMere dm
        left join dm.demandeur dmd
        left join dmd.departement dep
        where dm.dateDemande between :dateFrom and :dateTo
          and (
                :q = '' 
                or lower(dm.id) like lower(concat('%', :q, '%'))
                or lower(coalesce(dm.natureDemande, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(cast(dm.statutDemande as string), '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(dep.acronyme, '')) like lower(concat('%', :q, '%'))
          )
    """)
    Page<DemandeMere> search(
            @Param("q") String q,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );
}
