package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.repository.stock.StockMereRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StockMereService {
    @Autowired
    StockMereRepo stockMereRepo;
    public void insertStockMere(StockMere stockMere){
        this.stockMereRepo.save(stockMere);
    }

    public StockMere getStockMereById(int id){
        return this.stockMereRepo.findById(id).orElse(null);
    }
}
