package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieMereRepo;
import afg.achat.afgApprovAchat.repository.stock.StockFilleRepo;
import afg.achat.afgApprovAchat.repository.stock.StockMereRepo;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.stock.LotStockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class BonSortieService {
    private final BonSortieMereRepo bsMereRepo;
    private final BonSortieFilleRepo bsFilleRepo;
    private final DemandeFilleService demandeFilleService;
    private final DemandeMereService demandeMereService;
    private final StockFilleRepo stockFilleRepo;
    private final StockMereRepo stockMereRepo;
    private final LotStockService lotStockService;

    // ── Création d'un BS en brouillon ───────────────────────────────────────
    public BonSortieMere creerBrouillon(DemandeMere demande, Utilisateur mg) {
        long count = bsMereRepo.countByDemandeMere(demande);

        BonSortieMere bs = new BonSortieMere();
        bs.setNumeroBs("BS-" + demande.getId() + "-" + (count + 1));
        bs.setDemandeMere(demande);
        bs.setCreePar(mg);
        bs.setDateCreation(LocalDateTime.now());
        bs.setStatut(BonSortieMere.StatutBonSortie.BROUILLON);

        return bsMereRepo.save(bs);
    }

    // ── Ajout d'une ligne au brouillon ──────────────────────────────────────
    public BonSortieFille ajouterLigne(BonSortieMere bs,
                                       DemandeFille demandeFille,
                                       double quantiteSortie) {

        // Vérification : pas de dépassement de la quantité restante
        double dejasorti  = bsFilleRepo.sumQuantiteSortieByDemandeFille(demandeFille);
        double restant    = demandeFille.getQuantite() - dejasorti;

        if (quantiteSortie <= 0)
            throw new IllegalArgumentException("La quantité doit être positive.");
        if (quantiteSortie > restant)
            throw new IllegalArgumentException(
                    "Quantité demandée (" + quantiteSortie +
                            ") supérieure au restant à sortir (" + restant + ")."
            );

        BonSortieFille ligne = new BonSortieFille();
        ligne.setBonSortieMere(bs);
        ligne.setDemandeFille(demandeFille);
        ligne.setArticle(demandeFille.getArticle());
        ligne.setPrixUnitaire(demandeFille.getPrixUnitaire() != null
                ? demandeFille.getPrixUnitaire() : 0.0);
        ligne.setQuantiteSortie(quantiteSortie); // déclenche le calcul montantTotal

        return bsFilleRepo.save(ligne);
    }

    // ── Confirmation du BS → impact stock + recalcul etatLivraison ──────────
    public void confirmerBonSortie(int bsId) {
        BonSortieMere bs = bsMereRepo.findById(bsId)
                .orElseThrow(() -> new IllegalArgumentException("BS introuvable."));

        if (bs.getStatut() != BonSortieMere.StatutBonSortie.BROUILLON)
            throw new IllegalStateException("Ce bon de sortie est déjà confirmé ou annulé.");

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);
        if (lignes.isEmpty())
            throw new IllegalStateException("Impossible de confirmer un BS sans lignes.");

        DemandeMere demande = bs.getDemandeMere();

        // 1. Trouver ou créer le StockMere lié à cette demande
        StockMere stockMere = stockMereRepo.findByDemandeMere(demande)
                .orElseGet(() -> {
                    StockMere sm = new StockMere();
                    sm.setDemandeMere(demande);
                    return stockMereRepo.save(sm);
                });

        // 2. Pour chaque ligne : incrémenter StockFille.sortie
        for (BonSortieFille ligne : lignes) {
            StockFille sf = stockFilleRepo
                    .findByStockMereAndArticle(stockMere, ligne.getArticle())
                    .orElseGet(() -> {
                        StockFille nouveau = new StockFille();
                        nouveau.setStockMere(stockMere);
                        nouveau.setArticle(ligne.getArticle());
                        nouveau.setEntree(String.valueOf(0.0));
                        nouveau.setSortie(String.valueOf(0.0));
                        return nouveau;
                    });

            sf.setSortie(String.valueOf(sf.getSortie() + ligne.getQuantiteSortie()));
            stockFilleRepo.save(sf);

            try {
                lotStockService.consommerStock(
                        ligne.getArticle().getCodeArticle(),
                        ligne.getQuantiteSortie()
                );
            } catch (IllegalStateException e) {
                throw new IllegalStateException(
                        "Stock FIFO insuffisant pour l'article "
                                + ligne.getArticle().getDesignation()
                                + " : " + e.getMessage()
                );
            }
        }

        // 3. Passer le BS en CONFIRME
        bs.setStatut(BonSortieMere.StatutBonSortie.CONFIRME);
        bs.setDateConfirmation(LocalDateTime.now());
        bsMereRepo.save(bs);

        // 4. Recalculer etatLivraison de la demande
        recalculerEtatLivraison(demande);
    }

    // ── Recalcul EtatLivraison ───────────────────────────────────────────────
    private void recalculerEtatLivraison(DemandeMere demande) {
        List<DemandeFille> lignes = demandeFilleService
                .getDemandeFilleByDemandeMere(demande);

        double totalDemande = lignes.stream()
                .mapToDouble(DemandeFille::getQuantite).sum();

        double totalSorti = lignes.stream()
                .mapToDouble(l -> bsFilleRepo.sumQuantiteSortieByDemandeFille(l))
                .sum();

        if (totalSorti <= 0) {
            demande.setEtatLivraison(DemandeMere.EtatLivraison.NON_LIVREE);
        } else if (totalSorti >= totalDemande) {
            demande.setEtatLivraison(DemandeMere.EtatLivraison.SOLDEE);
        } else {
            demande.setEtatLivraison(DemandeMere.EtatLivraison.EN_COURS_SORTIE);
        }

        demandeMereService.saveDemandeMere(demande);
    }

    // ── Getters utiles pour la vue ───────────────────────────────────────────
    public List<BonSortieMere> getBonSortieByDemande(DemandeMere demande) {
        return bsMereRepo.findByDemandeMere(demande);
    }

    public double getQuantiteRestante(DemandeFille demandeFille) {
        double dejasorti = bsFilleRepo.sumQuantiteSortieByDemandeFille(demandeFille);
        return demandeFille.getQuantite() - dejasorti;
    }
}
