package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DemandeMereRepo
        extends JpaRepository<DemandeMere, String>,
        JpaSpecificationExecutor<DemandeMere> {

    boolean existsById(String id);
    Optional<DemandeMere> findByCodeProvisoire(String codeProvisoire);
}