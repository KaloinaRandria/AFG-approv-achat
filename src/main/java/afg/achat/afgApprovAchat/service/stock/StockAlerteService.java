package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.model.stock.StockAlerte;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.VEtatStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockAlerteService {
    @Autowired
    ArticleService articleService;
    @Autowired
    VEtatStockService vEtatStockService;

    public StockAlerte[] getAlertes() {

        VEtatStock[] etats = vEtatStockService.getAllEtatStocks();
        List<StockAlerte> alertes = new ArrayList<>();

        for (VEtatStock etat : etats) {

            double stock = Double.parseDouble(etat.getStockDisponible());
            double seuil = Double.parseDouble(etat.getSeuilMin());

            String type = null;

            if (stock <= 0) {
                type = "RUPTURE";
            } else if (stock <= seuil) {
                type = "SEUIL";
            }

            if (type != null) {
                alertes.add(new StockAlerte(
                        etat.getCodeArticle(),
                        etat.getDesignation(),
                        stock,
                        seuil,
                        type
                ));
            }
        }

        return alertes.toArray(new StockAlerte[0]);
    }
}
