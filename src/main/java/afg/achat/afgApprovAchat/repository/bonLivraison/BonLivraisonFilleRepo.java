package afg.achat.afgApprovAchat.repository.bonLivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BonLivraisonFilleRepo extends JpaRepository<BonLivraisonFille,Integer> {
}
