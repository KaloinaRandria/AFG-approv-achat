package afg.achat.afgApprovAchat.repository.demande.bonSortie;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BonSortieMereRepo extends JpaRepository<BonSortieMere, String> {
    Optional<BonSortieMere> findFirstByDemandeMereOrderByIdDesc(DemandeMere demandeMere);
    @Query("""
        select bs from BonSortieMere bs
        join bs.demandeMere dm
        where bs.dateSortie between :dateFrom and :dateTo
          and (:num = '' or lower(bs.id) like lower(concat('%', :num, '%')))
          and (:statut is null or bs.statut = :statut)
        order by bs.dateSortie desc
    """)
    Page<BonSortieMere> searchMulti(
            @Param("num")      String num,
            @Param("statut")   BonSortieMere.Statut statut,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo")   LocalDateTime dateTo,
            Pageable pageable
    );
}
