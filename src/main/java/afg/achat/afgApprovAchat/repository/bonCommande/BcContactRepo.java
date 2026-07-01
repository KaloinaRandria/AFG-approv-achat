package afg.achat.afgApprovAchat.repository.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BcContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BcContactRepo extends JpaRepository<BcContact,Integer> {
}
