package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.DeviseHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviseHistoriqueRepo extends JpaRepository<DeviseHistorique, Integer> {
    @Query("SELECT d FROM DeviseHistorique d WHERE d.devise.id = :idDevise")
    DeviseHistorique[] findDeviseHistoriqueByDevise(int idDevise);
}
