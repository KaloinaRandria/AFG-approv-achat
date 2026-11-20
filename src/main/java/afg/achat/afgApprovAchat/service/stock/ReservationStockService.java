package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Stock libre d'un article : les lots FIFO moins le stock réservé.
 *
 * <p>Une ligne de demande servie depuis le stock (type STOCK) réserve sa quantité non encore
 * sortie, pour toutes les demandes de matériel en cours ou validées. Sans cela, deux demandes
 * pouvaient se promettre le même stock et l'une restait bloquée : ni sortable, ni à acheter.</p>
 */
@Service
@RequiredArgsConstructor
public class ReservationStockService {

    /** Écart toléré sur les quantités décimales. */
    public static final double TOLERANCE = 0.001;

    private final LotStockService lotStockService;
    private final DemandeFilleRepo demandeFilleRepo;

    /**
     * Quantité réservée par les lignes STOCK de l'article, hors lignes exclues.
     *
     * @param lignesExclues lignes dont la réservation ne compte pas (celles que l'on sert ou modifie)
     */
    public double quantiteReservee(String codeArticle, Collection<Integer> lignesExclues) {
        // NOT IN () est invalide en SQL : liste sentinelle si rien n'est exclu
        Collection<Integer> exclues = lignesExclues == null || lignesExclues.isEmpty()
                ? List.of(-1) : lignesExclues;
        double promis = demandeFilleRepo.sommeQuantiteLignesStock(codeArticle, StatutDemande.REFUSE, exclues);
        double sorti  = demandeFilleRepo.sommeSortiesLignesStock(codeArticle, StatutDemande.REFUSE, exclues);
        return Math.max(0, promis - sorti);
    }

    /** Stock des lots FIFO moins le stock réservé par les autres lignes (jamais négatif). */
    public double stockLibre(String codeArticle, Collection<Integer> lignesExclues) {
        double stock = lotStockService.getStockDisponible(codeArticle);
        return Math.max(0, stock - quantiteReservee(codeArticle, lignesExclues));
    }
}
