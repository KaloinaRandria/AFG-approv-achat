package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.service.stock.ReservationStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demandes de matériel validées dont des articles restent à sortir du stock.
 *
 * <p>Scénario : à la validation, une partie des articles n'est pas en stock et doit être achetée.
 * Une fois les achats réceptionnés, le stock revient : les Moyens Généraux doivent alors faire le
 * bon de sortie. Une demande est « prête » dès qu'un de ses articles restant à sortir a du stock
 * libre (lots FIFO moins les réservations des autres demandes).</p>
 *
 * <p>Seules les demandes de matériel sont suivies : les anciennes demandes ne font pas de bon
 * de sortie.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SortieAFaireService {

    private final DemandeFilleRepo demandeFilleRepo;
    private final BonSortieFilleRepo bonSortieFilleRepo;
    private final ReservationStockService reservationStockService;

    /** Un article restant à sortir, avec le stock libre pour cette demande. */
    public record LigneASortir(DemandeFille ligne, double reste, double stockLibre) {
        /** Quantité sortable maintenant. */
        public double sortable() { return Math.max(0, Math.min(reste, stockLibre)); }

        public boolean disponible() { return sortable() > ReservationStockService.TOLERANCE; }
    }

    /** Une demande avec ses articles restant à sortir. */
    public record DemandeASortir(DemandeMere demande, List<LigneASortir> lignes) {
        /** Au moins un article peut sortir maintenant : le bon de sortie est à faire. */
        public boolean prete() { return lignes.stream().anyMatch(LigneASortir::disponible); }

        public long articlesDisponibles() { return lignes.stream().filter(LigneASortir::disponible).count(); }
    }

    /** Demandes avec des articles à sortir : prêtes (stock disponible) d'abord, puis par ancienneté. */
    public List<DemandeASortir> demandes() {
        Map<DemandeMere, List<DemandeFille>> parDemande = new LinkedHashMap<>();
        for (DemandeFille df : demandeFilleRepo.lignesASortir(StatutDemande.VALIDE, StatutDemande.REFUSE)) {
            parDemande.computeIfAbsent(df.getDemandeMere(), k -> new ArrayList<>()).add(df);
        }

        List<DemandeASortir> resultat = new ArrayList<>();
        parDemande.forEach((demande, lignes) -> {
            // Les réservations de la demande sont les siennes : stock libre réparti entre ses lignes,
            // les lignes STOCK d'abord (leur réservation), puis les lignes ACHAT
            Map<Integer, Double> restants = new HashMap<>();
            lignes.forEach(df -> restants.put(df.getId(),
                    df.getQuantite() - bonSortieFilleRepo.sumQuantiteSortieByDemandeFille(df)));
            Map<Integer, Double> stockParLigne = reservationStockService.repartirStockLibre(
                    lignes, df -> restants.get(df.getId()));
            List<LigneASortir> aSortir = lignes.stream()
                    .map(df -> new LigneASortir(df, restants.get(df.getId()),
                            stockParLigne.getOrDefault(df.getId(), 0.0)))
                    .toList();
            resultat.add(new DemandeASortir(demande, aSortir));
        });
        // Prêtes en premier ; l'ordre d'ancienneté de la requête est conservé (tri stable)
        resultat.sort((a, b) -> Boolean.compare(b.prete(), a.prete()));
        return resultat;
    }

    /** Nombre de demandes dont le bon de sortie peut être fait maintenant (tableau de bord). */
    public long nombrePretes() {
        return demandes().stream().filter(DemandeASortir::prete).count();
    }
}
