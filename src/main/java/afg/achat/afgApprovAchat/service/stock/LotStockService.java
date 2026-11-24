package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.stock.LotStock;
import afg.achat.afgApprovAchat.repository.stock.LotStockRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
        return creerLot(article, bl, quantite, prix, null);
    }

    /** Lot daté (ordre FIFO) ; {@code dateEntree} {@code null} : maintenant. */
    public LotStock creerLot(Article article, BonLivraisonMere bl,
                             double quantite, double prix, LocalDateTime dateEntree) {
        LotStock lot = LotStock.creer(article, bl, quantite, prix);
        if (dateEntree != null) lot.setDateEntree(dateEntree);
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

    // ── Remettre en stock (annulation d'un BS) ────────────────────────────
    /**
     * Remet une quantité en stock sous la forme d'un lot sans bon de livraison.
     * Les lots consommés par la sortie ne sont pas connus : le lot recréé est daté
     * de la sortie annulée, pour qu'il repasse en FIFO avant les lots reçus depuis.
     *
     * @param prix       prix unitaire du lot recréé (celui de la ligne de sortie)
     * @param dateEntree date d'entrée du lot recréé ; {@code null} : maintenant
     */
    public LotStock restituerStock(Article article, double quantite, double prix, LocalDateTime dateEntree) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantité à remettre en stock doit être positive.");
        }
        LotStock lot = LotStock.creer(article, null, quantite, prix);
        if (dateEntree != null) lot.setDateEntree(dateEntree);
        return lotStockRepo.save(lot);
    }
}
