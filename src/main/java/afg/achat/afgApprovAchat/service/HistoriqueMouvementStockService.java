package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.HistoriqueMouvementStockView;
import afg.achat.afgApprovAchat.repository.HistoriqueMouvementStockRepo;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HistoriqueMouvementStockService {

    private final HistoriqueMouvementStockRepo repo;

    public HistoriqueMouvementStockService(HistoriqueMouvementStockRepo repo) {
        this.repo = repo;
    }

    public List<HistoriqueMouvementStockView> getHistoriqueGlobal() {
        return repo.findAllOrderByDateDesc();
    }

    public List<HistoriqueMouvementStockView> getHistoriqueByArticle(String codeArticle) {
        return repo.findByCodeArticleOrderByDateMouvementDesc(codeArticle);
    }
}

