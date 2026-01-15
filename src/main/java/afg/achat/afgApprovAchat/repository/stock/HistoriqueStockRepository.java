package afg.achat.afgApprovAchat.repository.stock;

import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.service.stock.HistoriqueMouvementStockProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoriqueStockRepository extends JpaRepository<StockFille, Integer> {

    @Query(value = """
        SELECT *
        FROM v_historique_mouvement_stock
        WHERE id_article = :idArticle
        ORDER BY date_mouvement DESC
        """, nativeQuery = true)
    List<HistoriqueMouvementStockProjection> findByIdArticle(@Param("idArticle") int idArticle);

    // Optionnel : historique global
    @Query(value = """
        SELECT *
        FROM v_historique_mouvement_stock
        ORDER BY date_mouvement DESC
        """, nativeQuery = true)
    List<HistoriqueMouvementStockProjection> findAllHistorique();
}

