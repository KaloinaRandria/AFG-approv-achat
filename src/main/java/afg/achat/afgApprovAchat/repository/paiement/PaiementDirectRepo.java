package afg.achat.afgApprovAchat.repository.paiement;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.paiement.PaiementDirect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaiementDirectRepo extends JpaRepository<PaiementDirect, Long>, JpaSpecificationExecutor<PaiementDirect> {

    Optional<PaiementDirect> findByDemande(DemandeMere demande);

    Optional<PaiementDirect> findByDemandeId(String demandeId);

    boolean existsByDemande(DemandeMere demande);
}
