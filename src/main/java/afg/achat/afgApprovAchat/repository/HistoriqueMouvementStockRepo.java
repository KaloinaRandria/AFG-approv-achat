package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.HistoriqueMouvementStockView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HistoriqueMouvementStockRepo extends JpaRepository<HistoriqueMouvementStockView, String> {

    // historique global
    @Query("select h from HistoriqueMouvementStockView h order by h.dateMouvement desc")
    List<HistoriqueMouvementStockView> findAllOrderByDateDesc();

    // historique par article
    @Query("select h from HistoriqueMouvementStockView h where h.codeArticle = :codeArticle order by h.dateMouvement desc")
    List<HistoriqueMouvementStockView> findByCodeArticleOrderByDateMouvementDesc(String codeArticle);
}

