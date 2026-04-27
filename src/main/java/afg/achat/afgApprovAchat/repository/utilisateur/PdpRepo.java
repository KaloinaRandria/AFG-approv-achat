package afg.achat.afgApprovAchat.repository.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Pdp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PdpRepo extends JpaRepository<Pdp,Integer> {
}
