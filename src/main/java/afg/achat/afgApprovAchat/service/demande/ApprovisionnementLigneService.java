package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.service.stock.LotStockService;
import afg.achat.afgApprovAchat.service.stock.ReservationStockService;
import afg.achat.afgApprovAchat.service.util.FormatNumber;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static afg.achat.afgApprovAchat.service.stock.ReservationStockService.TOLERANCE;

/**
 * Répartition d'une ligne de demande entre le stock et l'achat.
 *
 * <p>Les droits (Moyens Généraux, étape) sont contrôlés par l'appelant ; ce service garantit
 * la cohérence des données : tout ou rien, et pas de réservation au-delà du stock libre.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ApprovisionnementLigneService {

    private final DemandeFilleRepo demandeFilleRepo;
    private final DemandeMereService demandeMereService;
    private final LotStockService lotStockService;
    private final ReservationStockService reservationStockService;
    private final BonSortieFilleRepo bonSortieFilleRepo;

    /**
     * Sert une partie (ou la totalité) d'une ligne à acheter depuis le stock.
     * Partie : la ligne garde la quantité à acheter et une ligne STOCK est créée pour le reste.
     * Totalité : la ligne passe en STOCK.
     *
     * @param prixRepli prix en ariary à utiliser si le prix FIFO ne peut pas être calculé
     * @return la ligne servie depuis le stock
     */
    public DemandeFille scinder(int ligneId, double qteStock, double prixRepli) {
        DemandeFille ligne = ligneModifiable(ligneId);
        if (ligne.getTypeApprovisionnement() == TypeApprovisionnement.STOCK) {
            throw new IllegalArgumentException("Cette ligne est déjà servie depuis le stock.");
        }

        double qteTotale = ligne.getQuantite();
        if (qteStock <= 0 || qteStock > qteTotale + TOLERANCE) {
            throw new IllegalArgumentException("Quantité stock invalide : entre 0 et "
                    + FormatNumber.quantite(qteTotale) + ".");
        }

        String codeArticle = ligne.getArticle().getCodeArticle();
        double libre = reservationStockService.stockLibre(codeArticle, List.of(ligne.getId()));
        if (qteStock > libre + TOLERANCE) {
            throw new IllegalArgumentException("Stock insuffisant : " + FormatNumber.quantite(libre)
                    + " disponible(s), une fois déduit le stock réservé par les autres demandes.");
        }

        // Prix FIFO en ariary, exprimé dans la devise de la demande
        double prixAriary;
        try {
            prixAriary = lotStockService.getPrixFifo(codeArticle, qteStock);
        } catch (RuntimeException e) {
            prixAriary = prixRepli;
        }
        double prix = ligne.getDemandeMere().depuisAriary(prixAriary);

        DemandeFille ligneStock;
        if (Math.abs(qteTotale - qteStock) <= TOLERANCE) {
            // Toute la quantité vient du stock
            ligne.setTypeApprovisionnement(TypeApprovisionnement.STOCK);
            ligne.setPrixUnitaire(prix);
            ligneStock = demandeFilleRepo.save(ligne);
        } else {
            ligne.setQuantite(String.valueOf(qteTotale - qteStock));
            ligne.setTypeApprovisionnement(TypeApprovisionnement.ACHAT);
            ligne.setPrixUnitaire(ligne.getPrixUnitaire()); // recalcule le montant estimé
            demandeFilleRepo.save(ligne);

            ligneStock = new DemandeFille();
            ligneStock.setDemandeMere(ligne.getDemandeMere());
            ligneStock.setArticle(ligne.getArticle());
            ligneStock.setQuantite(String.valueOf(qteStock));
            ligneStock.setPrixUnitaire(prix);
            ligneStock.setStatut(ligne.getStatut());
            ligneStock.setTypeApprovisionnement(TypeApprovisionnement.STOCK);
            ligneStock = demandeFilleRepo.save(ligneStock);
        }

        demandeMereService.recalculerTotal(ligne.getDemandeMere());
        return ligneStock;
    }

    /**
     * Remet une ligne servie depuis le stock à l'achat : elle libère sa réservation et sa
     * quantité non encore sortie apparaît dans les lignes à acheter (demande validée).
     * Sert quand le stock promis n'est plus là.
     */
    public DemandeFille remettreEnAchat(int ligneId) {
        DemandeFille ligne = ligneModifiable(ligneId);
        if (ligne.getTypeApprovisionnement() != TypeApprovisionnement.STOCK) {
            throw new IllegalArgumentException("Cette ligne n'est pas servie depuis le stock.");
        }

        double reste = ligne.getQuantite() - bonSortieFilleRepo.sumQuantiteSortieByDemandeFille(ligne);
        if (reste <= TOLERANCE) {
            throw new IllegalArgumentException("Cette ligne est déjà entièrement sortie du stock.");
        }

        ligne.setTypeApprovisionnement(TypeApprovisionnement.ACHAT);
        return demandeFilleRepo.save(ligne);
    }

    private DemandeFille ligneModifiable(int ligneId) {
        DemandeFille ligne = demandeFilleRepo.findById(ligneId)
                .orElseThrow(() -> new IllegalArgumentException("Ligne introuvable."));
        DemandeMere demande = ligne.getDemandeMere();
        if (ligne.getStatut() == StatutDemande.REFUSE || demande == null
                || demande.getStatut() == StatutDemande.REFUSE) {
            throw new IllegalArgumentException("Ligne ou demande refusée : rien à modifier.");
        }
        return ligne;
    }
}
