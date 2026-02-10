package afg.achat.afgApprovAchat.repository.stock;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockMereRepo extends JpaRepository<StockMere,Integer> {
    Optional<StockMere> findByBonLivraisonMere(BonLivraisonMere blMere);
}
