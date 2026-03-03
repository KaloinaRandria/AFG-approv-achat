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

        if (bs.getDemandeMere() == null || bs.getDemandeMere().getStatut() != StatutDemande.VALIDE) {
            throw new IllegalStateException("La demande n'est pas encore validée par le SG");
        }

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);
        if (lignes.isEmpty()) throw new IllegalArgumentException("Aucune ligne à sortir");

        boolean partiel = false;

        // 1) Vérif et ajustement des quantités selon le stock dispo
        for (BonSortieFille l : lignes) {

            if (l.getQuantiteSortie() < 0)
                throw new IllegalArgumentException("Quantité sortie négative interdite");

            if (l.getQuantiteSortie() > l.getQuantiteDemandee())
                throw new IllegalArgumentException("Quantité sortie > quantité demandée");

            String codeArticle = l.getArticle().getCodeArticle();
            double dispo = stockFilleService.getStockDisponible(codeArticle);

            if (dispo <= 0) {
                // Stock épuisé → mettre à 0 et ligne en attente
                l.setQuantiteSortie(0);
                l.setStatut(BonSortieFille.Statut.EN_ATTENTE);
                bsFilleRepo.save(l);
                partiel = true;
            } else if (l.getQuantiteSortie() > dispo) {
                // Sortie partielle possible
                l.setQuantiteSortie(dispo);
                l.setStatut(BonSortieFille.Statut.SORTIE);
                bsFilleRepo.save(l);
                partiel = true;
            } else {
                l.setStatut(BonSortieFille.Statut.SORTIE);
                bsFilleRepo.save(l);
            }
        }

        // 2) Créer StockMere
        StockMere sm = new StockMere();
        sm.setDemandeMere(bs.getDemandeMere());
        sm.setBonLivraisonMere(null);
        stockMereService.insertStockMere(sm);

        // 3) Écrire sorties effectives
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
        if (partiel) {
            bs.setStatut(BonSortieMere.Statut.PARTIELLE);
        } else {
            bs.setStatut(BonSortieMere.Statut.VALIDEE);
        }
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
