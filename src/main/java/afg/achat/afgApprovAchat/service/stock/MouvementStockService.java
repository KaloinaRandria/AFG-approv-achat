package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Point d'entrée unique des mouvements de stock hors réception de bon de commande
 * et bon de sortie (qui font déjà les deux écritures).
 * <p>
 * Le stock est tenu à deux endroits qui doivent rester égaux :
 * <ul>
 *   <li>les lots FIFO ({@code lot_stock}) : contrôle du disponible et valorisation des sorties ;</li>
 *   <li>les entrées / sorties ({@code stock_fille}, recopiées dans {@code stock_article} par trigger) :
 *       état de stock et alertes.</li>
 * </ul>
 * Chaque méthode met à jour les deux dans la même transaction.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MouvementStockService {

    private static final double TOLERANCE = 0.001;

    private final LotStockService lotStockService;
    private final StockMereService stockMereService;

    /** Entrée manuelle (hors bon de livraison), avec son propre en-tête de mouvement. */
    public void entreeManuelle(Article article, double quantite, double prixUnitaire) {
        entrer(nouvelEnTete(), article, quantite, prixUnitaire, null, null);
    }

    /** Sortie manuelle (hors bon de sortie), avec son propre en-tête de mouvement. */
    public void sortieManuelle(Article article, double quantite) {
        sortir(nouvelEnTete(), article, quantite);
    }

    private StockMere nouvelEnTete() {
        StockMere stockMere = new StockMere();
        stockMereService.insertStockMere(stockMere);
        return stockMere;
    }

    /**
     * Entrée en stock : nouveau lot FIFO et mouvement d'entrée.
     *
     * @param bl         bon de livraison d'origine ; {@code null} pour une entrée manuelle
     * @param dateEntree date du lot (ordre FIFO) ; {@code null} : maintenant
     */
    public void entrer(StockMere stockMere, Article article, double quantite, double prixUnitaire,
                       BonLivraisonMere bl, LocalDateTime dateEntree) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantité d'entrée doit être positive.");
        }
        if (prixUnitaire < 0) {
            throw new IllegalArgumentException("Le prix unitaire ne peut pas être négatif.");
        }
        lotStockService.creerLot(article, bl, quantite, prixUnitaire, dateEntree);
        stockMereService.addEntree(stockMere, article, quantite);
    }

    /**
     * Sortie de stock : consommation FIFO des lots et mouvement de sortie.
     * Refusée si les lots ne couvrent pas la quantité.
     */
    public void sortir(StockMere stockMere, Article article, double quantite) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantité de sortie doit être positive.");
        }
        double disponible = lotStockService.getStockDisponible(article.getCodeArticle());
        if (quantite > disponible + TOLERANCE) {
            throw new IllegalArgumentException("Stock insuffisant pour " + article.getCodeArticle()
                    + " : " + quantite + " demandé(s), " + disponible + " disponible(s).");
        }
        lotStockService.consommerStock(article.getCodeArticle(), quantite);
        stockMereService.addSortie(stockMere, article, quantite);
    }
}
