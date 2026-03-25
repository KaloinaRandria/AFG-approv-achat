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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BonSortieFilleService {

    @Autowired BonSortieMereRepo bsMereRepo;
    @Autowired BonSortieFilleRepo bsFilleRepo;
    @Autowired StockFilleService stockFilleService;
    @Autowired StockMereService stockMereService;
    @Autowired DemandeFilleService demandeFilleService;
    @Autowired DemandeMereService demandeMereService;

    @Transactional
    public void confirmerSortie(String bsId) {

        BonSortieMere bs = bsMereRepo.findById(bsId)
                .orElseThrow(() -> new IllegalArgumentException("BS introuvable : " + bsId));

        if (bs.getStatut() == BonSortieMere.Statut.VALIDEE) {
            throw new IllegalStateException("Ce bon de sortie est déjà entièrement validé.");
        }
        if (bs.getDemandeMere() == null
                || bs.getDemandeMere().getStatut() != StatutDemande.VALIDE) {
            throw new IllegalStateException("La demande n'est pas validée par le SG.");
        }

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);
        if (lignes == null || lignes.isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne dans ce bon de sortie.");
        }

        // ── Map dispo : une seule lecture BDD par article ────────────────────
        Map<String, Double> dispoMap = new HashMap<>();
        for (BonSortieFille l : lignes) {
            String code = l.getArticle().getCodeArticle();
            dispoMap.computeIfAbsent(code,
                    c -> stockFilleService.getStockDisponible(c));
        }

        // ── Étape 1 : validation ─────────────────────────────────────────────
        for (BonSortieFille l : lignes) {
            if (l.getQuantiteSortie() < 0)
                throw new IllegalArgumentException(
                        "Quantité négative pour : " + l.getArticle().getDesignation());
            if (l.getQuantiteSortie() > l.getQuantiteDemandee())
                throw new IllegalArgumentException(
                        "Quantité sortie > demandée pour : " + l.getArticle().getDesignation());
            if (l.getQuantiteSortie() == 0) continue;

            double dispo = dispoMap.get(l.getArticle().getCodeArticle());
            if (l.getQuantiteSortie() > dispo) {
                throw new IllegalStateException(
                        "Stock insuffisant pour « " + l.getArticle().getDesignation()
                                + " » (disponible : " + dispo
                                + ", saisi : " + l.getQuantiteSortie() + "). "
                                + "Veuillez re-enregistrer les quantités.");
            }
        }

        // ── Étape 2 : statuts lignes (pas de save() — géré par Hibernate) ───
        boolean partiel = false;
        for (BonSortieFille l : lignes) {
            double dispo = dispoMap.get(l.getArticle().getCodeArticle());
            if (dispo <= 0 || l.getQuantiteSortie() == 0) {
                l.setQuantiteSortie(0);
                l.setStatut(BonSortieFille.Statut.EN_ATTENTE);
                partiel = true;
            } else {
                l.setStatut(BonSortieFille.Statut.SORTIE);
            }
        }

        // ── Étape 3 : guard — aucune sortie effective ────────────────────────
        long nbSortiesEffectives = lignes.stream()
                .filter(l -> l.getQuantiteSortie() > 0).count();

        if (nbSortiesEffectives == 0) {
            bs.setStatut(BonSortieMere.Statut.PARTIELLE);
            bs.setDateSortie(LocalDateTime.now());
            // save explicite nécessaire ici car on return avant le flush naturel
            bsMereRepo.save(bs);
            updateLivraisonDemande(bs.getDemandeMere());
            return;
        }

        // ── Étape 4 : StockMere + StockFille ────────────────────────────────
        StockMere sm = new StockMere();
        sm.setDemandeMere(bs.getDemandeMere());
        sm.setBonLivraisonMere(null);
        stockMereService.insertStockMere(sm);

        for (BonSortieFille l : lignes) {
            if (l.getQuantiteSortie() == 0) continue;
            StockFille sf = new StockFille();
            sf.setStockMere(sm);
            sf.setArticle(l.getArticle());
            sf.setEntree("0");
            sf.setSortie(String.valueOf(l.getQuantiteSortie()));
            stockFilleService.insertStockFille(sf);
        }

        // ── Étape 5 : statut BS ──────────────────────────────────────────────
        bs.setStatut(partiel
                ? BonSortieMere.Statut.PARTIELLE
                : BonSortieMere.Statut.VALIDEE);
        bs.setDateSortie(LocalDateTime.now());

        // ── Étape 6 : MAJ livraison demande ─────────────────────────────────
        updateLivraisonDemande(bs.getDemandeMere());
    }

    // ────────────────────────────────────────────────────────────────────────
    private void updateLivraisonDemande(DemandeMere demande) {

        var lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        boolean anySortie = false;
        boolean allFullyDelivered = true;

        for (var l : lignes) {
            String code = l.getArticle().getCodeArticle();
            double demanded = l.getQuantite();
            double out = stockFilleService
                    .getTotalSortieByDemandeAndArticle(demande.getId(), code);

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