package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.ModeTraitement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModeTraitementRepo extends JpaRepository<ModeTraitement, Integer> {
    Optional<ModeTraitement> findFirstByLibelleIgnoreCase(String libelle);
}
