package afg.achat.afgApprovAchat.service.demande.bonSortie;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.bonSortie.BonSortieMereRepo;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.stock.StockFilleService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BonSortieFilleService {
    @Autowired
    BonSortieMereRepo bsMereRepo;
    @Autowired
    BonSortieFilleRepo bsFilleRepo;
    @Autowired
    StockFilleService stockFilleService;
    @Autowired
    StockMereService stockMereService;
    @Autowired
    DemandeFilleService demandeFilleService;
    @Autowired
    DemandeMereService demandeMereService;

    @Transactional
    public void confirmerSortie(String bsId) {

        BonSortieMere bs = bsMereRepo.findById(bsId)
                .orElseThrow(() -> new IllegalArgumentException("BS introuvable"));


        if (bs.getStatut() != BonSortieMere.Statut.CREE) {
            throw new IllegalStateException("BS déjà validé ou invalide");
        }

        //Sécurité: sortie possible seulement si la demande est validée SG
        if (bs.getDemandeMere() == null || bs.getDemandeMere().getStatut() != StatutDemande.VALIDE) {
            throw new IllegalStateException("La demande n'est pas encore validée par le SG");
        }

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);
        if (lignes.isEmpty()) throw new IllegalArgumentException("Aucune ligne à sortir");

        // 1) Vérif stock
        for (BonSortieFille l : lignes) {
            if (l.getQuantiteSortie() < 0) throw new IllegalArgumentException("Quantité sortie négative interdite");
            if (l.getQuantiteSortie() > l.getQuantiteDemandee())
                throw new IllegalArgumentException("Quantité sortie > quantité demandée");

            String codeArticle = l.getArticle().getCodeArticle();
            double dispo = stockFilleService.getStockDisponible(codeArticle);

            if (l.getQuantiteSortie() > dispo) {
                throw new IllegalArgumentException(
                        "Stock insuffisant pour " + l.getArticle().getDesignation()
                                + " (dispo=" + dispo + ", sortie=" + l.getQuantiteSortie() + ")"
                );
            }
        }

        // 2) Créer StockMere (lié à la demande)
        StockMere sm = new StockMere();
        sm.setDemandeMere(bs.getDemandeMere());
        sm.setBonLivraisonMere(null);
        stockMereService.insertStockMere(sm);

        // 3) Écrire sorties
        for (BonSortieFille l : lignes) {
            if (l.getQuantiteSortie() == 0) continue;

            StockFille sf = new StockFille();
            sf.setStockMere(sm);
            sf.setArticle(l.getArticle());
            sf.setEntree("0");
            sf.setSortie(String.valueOf(l.getQuantiteSortie()));
            stockFilleService.insertStockFille(sf);
        }

        // 4) Valider BS
        bs.setStatut(BonSortieMere.Statut.VALIDEE);
        bs.setDateSortie(LocalDateTime.now());
        bsMereRepo.save(bs);

        // 5) MAJ livraison demande
        updateLivraisonDemande(bs.getDemandeMere());
    }


    private void updateLivraisonDemande(DemandeMere demande) {

        // lignes demandées
        var lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        boolean anySortie = false;
        boolean allFullyDelivered = true;

        for (var l : lignes) {
            String code = l.getArticle().getCodeArticle();
            double demanded = l.getQuantite();

            double out = stockFilleService.getTotalSortieByDemandeAndArticle(demande.getId(), code);

            if (out > 0) anySortie = true;
            if (out + 1e-9 < demanded) {
                allFullyDelivered = false;
            }
        }

        if (!anySortie) {
            demande.setEtatLivraison(DemandeMere.EtatLivraison.NON_LIVREE);
        } else if (allFullyDelivered) {
            demande.setEtatLivraison(DemandeMere.EtatLivraison.LIVREE);
        } else {
            demande.setEtatLivraison(DemandeMere.EtatLivraison.PARTIELLE);
        }

        demandeMereService.saveDemandeMere(demande);
    }

}
