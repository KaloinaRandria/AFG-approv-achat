package afg.achat.afgApprovAchat.repository.stock;

import afg.achat.afgApprovAchat.model.stock.LotStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LotStockRepo extends JpaRepository<LotStock, Long> {

    // Lots disponibles triés FIFO (plus ancien en premier)
    @Query("""
        SELECT l FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
        ORDER BY l.dateEntree ASC
    """)
    List<LotStock> findLotsDisponibles(@Param("code") String code);

    // Stock total disponible
    @Query("""
        SELECT COALESCE(SUM(l.quantiteRestante), 0)
        FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
    """)
    double getStockDisponible(@Param("code") String code);

    // Valeur totale du stock (pour reporting)
    @Query("""
        SELECT COALESCE(SUM(l.quantiteRestante * l.prixUnitaire), 0)
        FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
    """)
    double getValeurTotaleStock(@Param("code") String code);

    // Prix FIFO simulé pour une quantité donnée
    @Query("""
        SELECT l FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
        ORDER BY l.dateEntree ASC
    """)
    List<LotStock> findLotsOrdonnes(@Param("code") String code);
}
