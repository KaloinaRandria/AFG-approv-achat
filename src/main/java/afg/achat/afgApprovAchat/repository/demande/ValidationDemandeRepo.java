package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationDemandeRepo extends JpaRepository<ValidationDemande, Integer> {
    List<ValidationDemande> findByDemandeMereOrderByDateActionDesc(DemandeMere demandeMere);
}
