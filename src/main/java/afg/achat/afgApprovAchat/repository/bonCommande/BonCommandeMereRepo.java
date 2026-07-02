package afg.achat.afgApprovAchat.repository.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonCommandeMereRepo extends JpaRepository<BonCommandeMere, String> {
	List<BonCommandeMere> findByDemandeMereOrderByDateCreationDesc(DemandeMere demandeMere);
}
