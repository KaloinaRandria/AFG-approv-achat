package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.model.stock.StockAlerte;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.VEtatStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockAlerteService {
    @Autowired
    ArticleService articleService;

    @Autowired
    VEtatStockService vEtatStockService;

    // Méthode existante pour la page de notifications
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

    // Nouvelle méthode pour obtenir les alertes par code article
    public Map<String, StockAlerte> getAlertesMap() {
        StockAlerte[] alertes = getAlertes();
        Map<String, StockAlerte> alertesMap = new HashMap<>();

        for (StockAlerte alerte : alertes) {
            alertesMap.put(alerte.getCodeArticle(), alerte);
        }

        return alertesMap;
    }

    // Méthode pour obtenir le nombre total d'alertes
    public int getAlertesCount() {
        return getAlertes().length;
    }

    // Méthode pour vérifier si un article spécifique a une alerte
    public StockAlerte getAlerteForArticle(String codeArticle) {
        Map<String, StockAlerte> alertesMap = getAlertesMap();
        return alertesMap.get(codeArticle);
    }
}