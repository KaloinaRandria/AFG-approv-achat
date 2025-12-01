package afg.achat.afgApprovAchat.repository.bonLivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BonLivraisonMereRepo extends JpaRepository<BonLivraisonMere,Integer> {
}
