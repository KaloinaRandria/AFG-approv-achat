package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.ModeTraitement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModeTraitementRepo extends JpaRepository<ModeTraitement, Integer> {
}
