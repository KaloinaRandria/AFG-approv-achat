package afg.achat.afgApprovAchat.repository.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockFilleRepo extends JpaRepository<StockFille,Integer> {
    @Query("""
        select coalesce(sum(sf.entree - sf.sortie), 0)
        from StockFille sf
        where sf.article.codeArticle = :codeArticle
    """)
    Double getStockDisponible(@Param("codeArticle") String codeArticle);

    @Query("""
    select coalesce(sum(sf.sortie), 0)
    from StockFille sf
    join sf.stockMere sm
    where sm.demandeMere.id = :demandeId
      and sf.article.codeArticle = :codeArticle
""")
    Double totalSortieByDemandeAndArticle(String demandeId, String codeArticle);

    Optional<StockFille> findByStockMereAndArticle(StockMere stockMere, Article article);

}
