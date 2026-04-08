package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.stock.LotStock;
import afg.achat.afgApprovAchat.repository.stock.LotStockRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class LotStockService {

    private final LotStockRepo lotStockRepo;

    public LotStockService(LotStockRepo lotStockRepo) {
        this.lotStockRepo = lotStockRepo;
    }

    // ── Créer un lot à la réception d'un BL ──────────────────────────────
    public LotStock creerLot(Article article, BonLivraisonMere bl,
                             double quantite, double prix) {
        LotStock lot = LotStock.creer(article, bl, quantite, prix);
        return lotStockRepo.save(lot);
    }

    // ── Stock disponible total ────────────────────────────────────────────
    public double getStockDisponible(String codeArticle) {
        return lotStockRepo.getStockDisponible(codeArticle);
    }

    // ── Prix FIFO pour une quantité demandée ─────────────────────────────
    // Retourne le prix moyen pondéré des lots qui seront consommés
    public double getPrixFifo(String codeArticle, double quantiteDemandee) {
        List<LotStock> lots = lotStockRepo.findLotsDisponibles(codeArticle);

        double restant     = quantiteDemandee;
        double valeurTotale = 0;

        for (LotStock lot : lots) {
            if (restant <= 0) break;
            double qtePrelevee = Math.min(lot.getQuantiteRestante(), restant);
            valeurTotale      += qtePrelevee * lot.getPrixUnitaire();
            restant           -= qtePrelevee;
        }

        if (restant > 0) {
            throw new IllegalStateException(
                    "Stock insuffisant pour " + codeArticle +
                            " — manque " + restant
            );
        }

        return quantiteDemandee > 0 ? valeurTotale / quantiteDemandee : 0;
    }

    // ── Consommer le stock en FIFO (à l'émission d'un BS) ────────────────
    public void consommerStock(String codeArticle, double quantite) {
        List<LotStock> lots = lotStockRepo.findLotsDisponibles(codeArticle);

        double aConsommer = quantite;

        for (LotStock lot : lots) {
            if (aConsommer <= 0) break;

            double qtePrelevee = Math.min(lot.getQuantiteRestante(), aConsommer);
            lot.setQuantiteRestante(lot.getQuantiteRestante() - qtePrelevee);
            aConsommer -= qtePrelevee;
            lotStockRepo.save(lot);
        }

        if (aConsommer > 0.001) { // tolérance flottant
            throw new IllegalStateException(
                    "Stock insuffisant pour " + codeArticle
            );
        }
    }

    // ── Annuler une consommation (si BS annulé) ───────────────────────────
    public void restituerStock(String codeArticle, double quantite,
                               BonLivraisonMere bl) {
        // Recréer un lot avec la quantité restituée
        // On retrouve l'article via le dernier lot existant
        LotStock reference = lotStockRepo.findLotsDisponibles(codeArticle)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Article introuvable"));

        LotStock lotRestitue = LotStock.creer(
                reference.getArticle(), bl, quantite,
                getPrixFifo(codeArticle, 0) // prix moyen actuel
        );
        lotStockRepo.save(lotRestitue);
    }
}
