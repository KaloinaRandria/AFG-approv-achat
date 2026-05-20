package afg.achat.afgApprovAchat.repository.bonSortie;

import afg.achat.afgApprovAchat.model.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonSortieFilleRepo extends JpaRepository<BonSortieFille, Integer> {
    List<BonSortieFille> findByBonSortieMere(BonSortieMere bonSortieMere);
    List<BonSortieFille> findByDemandeFille(DemandeFille demandeFille);

    // Total sorti pour une DemandeFille (toutes BS confondues, statut CONFIRME)
    @Query("""
        SELECT COALESCE(SUM(f.quantiteSortie), 0)
        FROM BonSortieFille f
        WHERE f.demandeFille = :demandeFille
          AND f.bonSortieMere.statut = 'CONFIRME'
    """)
    double sumQuantiteSortieByDemandeFille(@Param("demandeFille") DemandeFille demandeFille);
}
