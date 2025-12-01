package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.VEtatStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VEtatStockRepo extends JpaRepository<VEtatStock, Integer> {
    @Query("SELECT v FROM VEtatStock v WHERE v.codeArticle = :codeArticle")
    VEtatStock findByCodeArticle(String codeArticle);
}
