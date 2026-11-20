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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // ── Création + confirmation en une seule transaction ────────────────────
    /**
     * Crée le bon de sortie et le confirme (sortie de stock) en une seule transaction :
     * à la moindre erreur (ligne invalide, quantité supérieure au restant, stock
     * insuffisant), rien n'est enregistré, ni bon de sortie ni mouvement de stock.
     *
     * @param quantitesParLigne quantité à sortir par identifiant de ligne de demande ;
     *                          les quantités nulles ou négatives sont ignorées
     */
    public BonSortieMere creerEtConfirmer(DemandeMere demande, Utilisateur mg, String commentaire,
                                          Map<Integer, Double> quantitesParLigne) {
        // ── 1. Contrôles (avant toute écriture) ──
        Map<DemandeFille, Double> aSortir = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> e : quantitesParLigne.entrySet()) {
            Double quantite = e.getValue();
            if (quantite == null || quantite <= 0) continue;

            DemandeFille df = demandeFilleService.getDemandeFilleById(e.getKey());
            // La ligne doit appartenir à la demande du bon de sortie
            if (df == null || df.getDemandeMere() == null
                    || !demande.getId().equals(df.getDemandeMere().getId())) {
                throw new IllegalArgumentException("Ligne de demande invalide pour cette demande.");
            }

            String codeArticle = df.getArticle().getCodeArticle();
            double stock = lotStockService.getStockDisponible(codeArticle);
            if (quantite > stock + 0.001) {
                throw new IllegalArgumentException("Stock insuffisant pour " + df.getArticle().getDesignation()
                        + " : " + quantite + " demandé(s), " + stock + " disponible(s).");
            }
            aSortir.put(df, quantite);
        }
        if (aSortir.isEmpty()) {
            throw new IllegalArgumentException("Veuillez saisir une quantité supérieure à 0 pour au moins un article.");
        }

        // ── 2. Écritures ──
        BonSortieMere bs = creerBrouillon(demande, mg);
        bs.setCommentaire(commentaire == null ? null : commentaire.trim());
        bsMereRepo.save(bs);
        // ajouterLigne contrôle aussi le restant à sortir de chaque ligne
        aSortir.forEach((df, quantite) -> ajouterLigne(bs, df, quantite));
        confirmerBonSortie(bs.getId());
        return bs;
    }

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

    // ── Annulation d'un BS confirmé → remise en stock ───────────────────────
    /**
     * Annule un bon de sortie confirmé, en une seule transaction : les quantités
     * sorties sont remises en stock, le bon passe en ANNULE (avec son motif) et
     * l'état de livraison de la demande est recalculé ; les quantités redeviennent
     * disponibles pour un nouveau bon de sortie.
     */
    public BonSortieMere annulerBonSortie(int bsId, Utilisateur auteur, String motif) {
        String motifNettoye = motif == null ? "" : motif.trim();
        if (motifNettoye.isEmpty()) {
            throw new IllegalArgumentException("Le motif d'annulation est obligatoire.");
        }

        BonSortieMere bs = bsMereRepo.findByIdPourMiseAJour(bsId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de sortie introuvable."));
        if (bs.getStatut() != BonSortieMere.StatutBonSortie.CONFIRME) {
            throw new IllegalStateException("Seul un bon de sortie confirmé peut être annulé.");
        }

        DemandeMere demande = bs.getDemandeMere();
        StockMere stockMere = stockMereRepo.findByDemandeMere(demande).orElse(null);
        LocalDateTime dateSortie = bs.getDateConfirmation() != null ? bs.getDateConfirmation() : bs.getDateCreation();

        for (BonSortieFille ligne : bsFilleRepo.findByBonSortieMere(bs)) {
            double quantite = ligne.getQuantiteSortie();
            if (quantite <= 0) continue;

            // Mouvements de stock de la demande : on retire la sortie enregistrée
            if (stockMere != null) {
                stockFilleRepo.findByStockMereAndArticle(stockMere, ligne.getArticle()).ifPresent(sf -> {
                    sf.setSortie(String.valueOf(Math.max(0.0, sf.getSortie() - quantite)));
                    stockFilleRepo.save(sf);
                });
            }
            // Lots FIFO : la quantité redevient disponible
            lotStockService.restituerStock(ligne.getArticle(), quantite, ligne.getPrixUnitaire(), dateSortie);
        }

        bs.setStatut(BonSortieMere.StatutBonSortie.ANNULE);
        bs.setDateAnnulation(LocalDateTime.now());
        bs.setAnnulePar(auteur);
        bs.setMotifAnnulation(motifNettoye);
        bsMereRepo.save(bs);

        recalculerEtatLivraison(demande);
        return bs;
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
    /**
     * Bons de sortie à afficher pour une demande. Les brouillons sont exclus :
     * un bon est toujours créé et confirmé d'un coup, un brouillon n'est donc
     * qu'un reste d'une création interrompue (avant la mise en transaction unique).
     */
    public List<BonSortieMere> getBonSortieByDemande(DemandeMere demande) {
        return bsMereRepo.findByDemandeMere(demande).stream()
                .filter(bs -> bs.getStatut() != BonSortieMere.StatutBonSortie.BROUILLON)
                .toList();
    }

    // ── Liste des bons de sortie ─────────────────────────────────────────────

    /** Statuts proposés dans la liste ; les brouillons (créations interrompues) n'y figurent jamais. */
    public static final List<BonSortieMere.StatutBonSortie> STATUTS_LISTE =
            List.of(BonSortieMere.StatutBonSortie.CONFIRME, BonSortieMere.StatutBonSortie.ANNULE);

    /**
     * Recherche paginée des bons de sortie (hors brouillons).
     *
     * @param statut statut demandé ; {@code null} ou BROUILLON : confirmés et annulés
     * @param du     début de période inclus ; {@code null} : sans limite
     * @param au     fin de période incluse (toute la journée) ; {@code null} : sans limite
     */
    public Page<BonSortieMere> rechercher(BonSortieMere.StatutBonSortie statut, LocalDate du, LocalDate au,
                                          String numero, String demandeur, String article, Pageable pageable) {
        List<BonSortieMere.StatutBonSortie> statuts =
                (statut == null || !STATUTS_LISTE.contains(statut)) ? STATUTS_LISTE : List.of(statut);
        return bsMereRepo.rechercher(statuts,
                du == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : du.atStartOfDay(),
                au == null ? LocalDateTime.of(2999, 12, 31, 23, 59, 59) : au.atTime(LocalTime.MAX),
                texte(numero), texte(demandeur), texte(article), pageable);
    }

    /** Nombre de lignes et montant total de chaque bon, par identifiant de bon. */
    public Map<Integer, double[]> totauxParBon(List<BonSortieMere> bons) {
        Map<Integer, double[]> totaux = new LinkedHashMap<>();
        if (bons.isEmpty()) return totaux;
        for (Object[] r : bsFilleRepo.totauxParBon(bons)) {
            totaux.put((Integer) r[0], new double[]{((Number) r[1]).doubleValue(), ((Number) r[2]).doubleValue()});
        }
        return totaux;
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur.trim();
    }

    public double getQuantiteRestante(DemandeFille demandeFille) {
        double dejasorti = bsFilleRepo.sumQuantiteSortieByDemandeFille(demandeFille);
        return demandeFille.getQuantite() - dejasorti;
    }
}
