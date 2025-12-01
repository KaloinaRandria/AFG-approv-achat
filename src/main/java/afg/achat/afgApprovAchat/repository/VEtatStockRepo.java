package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.VEtatStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VEtatStockRepo extends JpaRepository<VEtatStock, Integer>
{
}
