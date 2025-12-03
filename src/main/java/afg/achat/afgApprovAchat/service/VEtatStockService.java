package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.repository.VEtatStockRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VEtatStockService {
    @Autowired
    VEtatStockRepo vEtatStockRepo;

    public VEtatStock[] getAllEtatStocks() {
        return vEtatStockRepo.findAll().toArray(new VEtatStock[0]);
    }

    public VEtatStock getEtatStockByCode(String codeArticle) {
        return vEtatStockRepo.findByCodeArticle(codeArticle);
    }
}
