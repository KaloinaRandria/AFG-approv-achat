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

        // Correction 1 : PARTIELLE peut aussi être confirmé
        if (bs.getStatut() == BonSortieMere.Statut.VALIDEE) {
            throw new IllegalStateException("Ce bon de sortie est déjà entièrement validé");
        }

        if (bs.getDemandeMere() == null
                || bs.getDemandeMere().getStatut() != StatutDemande.VALIDE) {
            throw new IllegalStateException("La demande n'est pas encore validée");
        }

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);
        if (lignes.isEmpty()) throw new IllegalArgumentException("Aucune ligne à sortir");

        // Correction 3 : passe de validation avant toute écriture
        for (BonSortieFille l : lignes) {
            if (l.getQuantiteSortie() < 0)
                throw new IllegalArgumentException(
                        "Quantité négative interdite pour : " + l.getArticle().getDesignation());
            if (l.getQuantiteSortie() > l.getQuantiteDemandee())
                throw new IllegalArgumentException(
                        "Quantité sortie > demandée pour : " + l.getArticle().getDesignation());
        }

        boolean partiel = false;
        double totalBS  = 0.0;

        // Boucle principale : stock + prix + statut ligne
        for (BonSortieFille l : lignes) {

            String codeArticle = l.getArticle().getCodeArticle();
            double dispo = stockFilleService.getStockDisponible(codeArticle);

            if (dispo <= 0) {
                l.setQuantiteSortie(0);
                l.setStatut(BonSortieFille.Statut.EN_ATTENTE);
                partiel = true;
            } else if (l.getQuantiteSortie() > dispo) {
                l.setQuantiteSortie(dispo);
                l.setStatut(BonSortieFille.Statut.SORTIE);
                partiel = true;
            } else {
                l.setStatut(l.getQuantiteSortie() > 0
                        ? BonSortieFille.Statut.SORTIE
                        : BonSortieFille.Statut.EN_ATTENTE);
                if (l.getQuantiteSortie() == 0) partiel = true;
            }

            // Correction 2 : snapshot prix depuis DemandeFille si absent
            if (l.getPrixUnitaire() == null) {
                double prix = demandeFilleService
                        .getPrixByDemandeAndArticle(
                                bs.getDemandeMere().getId(),
                                codeArticle)
                        .orElse(0.0);
                l.setPrixUnitaire(prix);
            }

            totalBS += l.getQuantiteSortie()
                    * (l.getPrixUnitaire() != null ? l.getPrixUnitaire() : 0.0);

            bsFilleRepo.save(l);
        }

        // StockMere
        StockMere sm = new StockMere();
        sm.setDemandeMere(bs.getDemandeMere());
        sm.setBonLivraisonMere(null);
        stockMereService.insertStockMere(sm);

        // Écritures stock
        for (BonSortieFille l : lignes) {
            if (l.getQuantiteSortie() == 0) continue;

            StockFille sf = new StockFille();
            sf.setStockMere(sm);
            sf.setArticle(l.getArticle());
            sf.setEntree("0");
            sf.setSortie(String.valueOf(l.getQuantiteSortie()));
            stockFilleService.insertStockFille(sf);
        }

        // Correction 2 : persister le total valorisé
        bs.setTotalPrix(totalBS);
        bs.setStatut(partiel ? BonSortieMere.Statut.PARTIELLE : BonSortieMere.Statut.VALIDEE);
        bs.setDateSortie(LocalDateTime.now());
        bsMereRepo.save(bs);

        // MAJ etatLivraison demande
        updateLivraisonDemande(bs.getDemandeMere());
    }

    private void updateLivraisonDemande(DemandeMere demande) {

        var lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        boolean anySortie        = false;
        boolean allFullyDelivered = true;

        for (var l : lignes) {

            // Correction 4 : ignorer les lignes refusées
            if (l.getStatut() == StatutDemande.REFUSE) continue;

            double demanded = l.getQuantite();
            double out = stockFilleService.getTotalSortieByDemandeAndArticle(
                    demande.getId(),
                    l.getArticle().getCodeArticle()
            );

            if (out > 0) anySortie = true;
            if (out + 1e-9 < demanded) allFullyDelivered = false;
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
