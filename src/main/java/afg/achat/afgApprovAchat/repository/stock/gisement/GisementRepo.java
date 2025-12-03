package afg.achat.afgApprovAchat.repository.stock.gisement;

import afg.achat.afgApprovAchat.model.stock.gisement.ExistantGisement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GisementRepo extends JpaRepository<ExistantGisement, Integer> {
}
