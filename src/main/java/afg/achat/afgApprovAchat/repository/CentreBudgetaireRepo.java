package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CentreBudgetaireRepo extends JpaRepository<CentreBudgetaire, Integer> {
    @Query("SELECT c FROM CentreBudgetaire c WHERE c.codeCentre = :codeCentre")
    CentreBudgetaire findCentreBudgetaireByCodeCentre(String codeCentre);
}
