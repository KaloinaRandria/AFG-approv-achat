package afg.achat.afgApprovAchat.repository.stock.gisement;

import afg.achat.afgApprovAchat.model.stock.gisement.GisementStockFille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GisementStockFilleRepo extends JpaRepository<GisementStockFille,Integer> {
}
