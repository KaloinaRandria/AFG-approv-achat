package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.DeviseHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviseHistoriqueRepo extends JpaRepository<DeviseHistorique, Integer> {
}
