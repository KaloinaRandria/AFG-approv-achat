package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.repository.stock.StockFilleRepo;
import afg.achat.afgApprovAchat.repository.stock.StockMereRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StockMereService {
    @Autowired
    StockMereRepo stockMereRepo;
    @Autowired
    StockFilleRepo stockFilleRepo;
    public void insertStockMere(StockMere stockMere){
        this.stockMereRepo.save(stockMere);
    }

    public StockMere getStockMereById(int id){
        return this.stockMereRepo.findById(id).orElse(null);
    }

    public StockMere getOrCreateStockMere(BonLivraisonMere blMere) {
        return stockMereRepo.findByBonLivraisonMere(blMere)
                .orElseGet(() -> stockMereRepo.save(new StockMere(null, blMere)));
    }

    public void addEntree(StockMere stockMere, Article article, double qte) {
        StockFille sf = stockFilleRepo.findByStockMereAndArticle(stockMere, article)
                .orElseGet(() -> {
                    StockFille n = new StockFille();
                    n.setStockMere(stockMere);
                    n.setArticle(article);
                    n.setEntree("0");
                    n.setSortie("0");
                    return n;
                });

        sf.setEntree(String.valueOf(sf.getEntree() + qte));
        // sortie reste 0 ici
        stockFilleRepo.save(sf);
    }
}
