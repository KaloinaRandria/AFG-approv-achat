package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

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

    /**
     * Stock sortable par ligne d'une même demande.
     *
     * <p>Par article, le stock libre de la demande (lots moins les réservations des autres
     * demandes) va d'abord aux lignes STOCK, à hauteur de leur reste à sortir : c'est leur
     * réservation. Les lignes ACHAT n'ont que ce qui dépasse (en pratique, les quantités achetées
     * une fois réceptionnées). Sans cette priorité, une ligne scindée « 5 en stock + 5 à
     * l'achat » offrait les 5 réservés aux deux lignes, et le stock pouvait partir sur la ligne
     * achat au détriment de la ligne stock.</p>
     *
     * @param lignes  lignes de la demande (leurs réservations ne sont pas déduites du stock libre)
     * @param restant reste à sortir de chaque ligne
     * @return stock sortable par identifiant de ligne (0 pour une ligne sans stock)
     */
    public Map<Integer, Double> repartirStockLibre(Collection<DemandeFille> lignes,
                                                   ToDoubleFunction<DemandeFille> restant) {
        List<Integer> ids = lignes.stream().map(DemandeFille::getId).toList();
        Map<String, List<DemandeFille>> parArticle = new LinkedHashMap<>();
        for (DemandeFille df : lignes) {
            parArticle.computeIfAbsent(df.getArticle().getCodeArticle(), k -> new ArrayList<>()).add(df);
        }

        Map<Integer, Double> sortable = new HashMap<>();
        parArticle.forEach((codeArticle, lignesArticle) -> {
            double reste = stockLibre(codeArticle, ids);
            // Lignes STOCK d'abord (leur réservation), puis les autres ; à type égal, ordre de saisie
            List<DemandeFille> ordre = lignesArticle.stream()
                    .sorted(Comparator.comparing((DemandeFille df) -> !estLigneStock(df))
                            .thenComparingInt(DemandeFille::getId))
                    .toList();
            for (DemandeFille df : ordre) {
                double part = Math.max(0, Math.min(reste, restant.applyAsDouble(df)));
                sortable.put(df.getId(), part);
                // Une ligne STOCK garde toute sa réservation, même si on n'en sort qu'une partie
                reste -= part;
            }
        });
        return sortable;
    }

    private static boolean estLigneStock(DemandeFille df) {
        return df.getTypeApprovisionnement() == DemandeFille.TypeApprovisionnement.STOCK;
    }
}
