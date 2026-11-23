package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeAutreDetail;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DemandeAutreDetailRepo extends JpaRepository<DemandeAutreDetail, Integer> {
    Optional<DemandeAutreDetail> findByDemandeMere(DemandeMere demandeMere);
}
