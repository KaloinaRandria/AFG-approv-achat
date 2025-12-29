package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.Adresse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdresseRepo extends JpaRepository<Adresse, Integer> {
}
