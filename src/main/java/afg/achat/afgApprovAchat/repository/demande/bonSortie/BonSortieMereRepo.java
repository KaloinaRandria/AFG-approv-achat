package afg.achat.afgApprovAchat.repository.demande.bonSortie;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BonSortieMereRepo extends JpaRepository<BonSortieMere, String> {
    Optional<BonSortieMere> findFirstByDemandeMereOrderByIdDesc(DemandeMere demandeMere);
}
