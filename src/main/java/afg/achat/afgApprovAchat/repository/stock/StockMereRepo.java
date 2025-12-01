package afg.achat.afgApprovAchat.repository.stock;

import afg.achat.afgApprovAchat.model.stock.StockMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMereRepo extends JpaRepository<StockMere,Integer> {
}
