package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.repository.stock.StockFilleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StockFilleService {
    @Autowired
    StockFilleRepo stockFilleRepo;

    public void insertStockFille(StockFille stockFille) {
        this.stockFilleRepo.save(stockFille);
    }

    public Double getStockDisponible(String codeArticle) {
        return this.stockFilleRepo.getStockDisponible(codeArticle);
    }

    public Double getTotalSortieByDemandeAndArticle(String demandeId, String codeArticle) {
        return this.stockFilleRepo.totalSortieByDemandeAndArticle(demandeId, codeArticle);
    }

}
