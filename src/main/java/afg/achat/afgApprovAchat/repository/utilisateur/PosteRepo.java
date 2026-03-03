package afg.achat.afgApprovAchat.repository.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Poste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PosteRepo extends JpaRepository<Poste, Integer> {
}
