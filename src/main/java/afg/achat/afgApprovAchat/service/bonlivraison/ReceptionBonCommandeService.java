package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutReception;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.PrixArticle;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonFilleRepo;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonMereRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.stock.LotStockService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.util.PrixArticleService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Réception d'un bon de commande validé : chaque réception crée un bon de livraison rattaché
 * au bon de commande (en-tête et lignes), fait entrer les quantités en stock et met à jour
 * le reçu / reste à recevoir de chaque ligne et le statut de réception du bon.
 */
@Service
@RequiredArgsConstructor
public class ReceptionBonCommandeService {

    /** Tolérance sur les comparaisons de quantités (saisies décimales). */
    private static final double EPSILON = 1e-6;

    private final BonCommandeMereRepo bonCommandeMereRepo;
    private final BonCommandeFilleRepo bonCommandeFilleRepo;
    private final BonLivraisonMereRepo bonLivraisonMereRepo;
    private final BonLivraisonFilleRepo bonLivraisonFilleRepo;
    private final ArticleService articleService;
    private final DeviseService deviseService;
    private final PrixArticleService prixArticleService;
    private final LotStockService lotStockService;
    private final StockMereService stockMereService;
    private final IdGenerator idGenerator;

    /** Saisie d'une ligne : quantité reçue, prix unitaire et, pour une ligne sans article, l'article choisi. */
    public record LigneSaisie(String quantiteRecue, String prixUnitaire, String codeArticle) {
    }

    /**
     * Saisie d'une réception.
     *
     * @param lignes saisie par identifiant de ligne du bon de commande ; une ligne absente ou à 0 n'est pas reçue
     */
    public record SaisieReception(String deviseId, String dateReception, String description,
                                  Map<Integer, LigneSaisie> lignes) {
    }

    // ── Consultation ─────────────────────────────────────────────────────────

    public List<BonLivraisonMere> getBonsLivraison(BonCommandeMere bc) {
        return bonLivraisonMereRepo.findByBonCommandeMereOrderByDateReceptionDesc(bc);
    }

    // ── Réception ────────────────────────────────────────────────────────────

    /** Enregistre une réception du bon de commande et renvoie le bon de livraison créé. */
    @Transactional
    public BonLivraisonMere receptionner(String idBc, SaisieReception saisie) {
        // Verrou : deux réceptions simultanées du même bon ne peuvent pas dépasser le commandé
        BonCommandeMere bc = bonCommandeMereRepo.verrouiller(idBc)
                .orElseThrow(() -> new WorkflowException("Bon de commande introuvable."));
        if (!bc.estReceptionnable()) {
            throw new WorkflowException("Ce bon de commande ne peut pas être réceptionné : il doit être validé"
                    + " et sa réception ne doit être ni complète ni soldée.");
        }

        // ── 1. Contrôles (aucune écriture avant qu'ils soient tous passés) ──
        Devise devise = lireDevise(saisie.deviseId());
        LocalDateTime dateReception = lireDate(saisie.dateReception());
        List<BonCommandeFille> lignesBc = bonCommandeFilleRepo.findBonCommandeFillesByBonCommandeMere(bc);
        Map<Integer, LigneSaisie> saisies = saisie.lignes() == null ? Map.of() : saisie.lignes();

        List<LigneRecue> recues = new ArrayList<>();
        for (BonCommandeFille ligne : lignesBc) {
            LigneSaisie s = saisies.get(ligne.getId());
            if (s == null) continue;
            String libelle = "Ligne « " + ligne.getDesignationAffichee() + " »";

            double quantite = lireNombre(s.quantiteRecue(), libelle + " : quantité reçue");
            if (quantite <= EPSILON) continue;
            double reste = ligne.getResteARecevoir();
            if (quantite > reste + EPSILON) {
                throw new WorkflowException(libelle + " : la quantité reçue (" + format(quantite)
                        + ") dépasse le reste à recevoir (" + format(reste) + ").");
            }
            double prix = lireNombre(s.prixUnitaire(), libelle + " : prix unitaire");
            Article article = articleDeLaLigne(ligne, s.codeArticle(), libelle);
            recues.add(new LigneRecue(ligne, article, quantite, prix, reste));
        }
        if (recues.isEmpty()) {
            throw new WorkflowException("Saisissez au moins une ligne avec une quantité reçue.");
        }

        // ── 2. Bon de livraison ──
        BonLivraisonMere bl = new BonLivraisonMere();
        bl.setId(idGenerator);
        bl.setBonCommandeMere(bc);
        bl.setFournisseur(bc.getFournisseur());
        bl.setDevise(devise);
        bl.setDateReception(dateReception.toString());
        String description = saisie.description() == null ? "" : saisie.description().trim();
        bl.setDescription(description.isEmpty() ? "Réception du bon de commande " + numero(bc) : description);
        bl.setTotalPrix(recues.stream().mapToDouble(r -> r.quantite() * r.prix()).sum());
        bonLivraisonMereRepo.save(bl);

        StockMere entreeStock = stockMereService.getOrCreateStockMere(bl);
        for (LigneRecue r : recues) {
            BonLivraisonFille blf = new BonLivraisonFille();
            blf.setBonLivraisonMere(bl);
            blf.setBonCommandeFille(r.ligne());
            blf.setArticle(r.article());
            blf.setQuantiteDemande(String.valueOf(r.resteAvant()));
            blf.setQuantiteRecu(String.valueOf(r.quantite()));
            blf.setPrixUnitaire(String.valueOf(r.prix()));
            bonLivraisonFilleRepo.save(blf);

            // Historique du prix, lot valorisé (FIFO) et mouvement d'entrée
            PrixArticle prixArticle = new PrixArticle();
            prixArticle.setArticle(r.article());
            prixArticle.setBonLivraisonMere(bl);
            prixArticle.setPrixUnitaire(r.prix());
            prixArticle.setDatePrix(LocalDate.from(dateReception));
            prixArticleService.insert(prixArticle);
            lotStockService.creerLot(r.article(), bl, r.quantite(), r.prix());
            stockMereService.addEntree(entreeStock, r.article(), r.quantite());

            // Suivi de la ligne du bon de commande
            BonCommandeFille ligne = r.ligne();
            ligne.setQuantiteRecue(ligne.getQuantiteRecue() + r.quantite());
            ligne.setQuantiteRestante(ligne.getResteARecevoir());
            bonCommandeFilleRepo.save(ligne);
        }

        // ── 3. Statut de réception du bon ──
        boolean toutRecu = lignesBc.stream().allMatch(l -> l.getResteARecevoir() <= EPSILON);
        bc.setStatutReception(toutRecu ? StatutReception.RECU : StatutReception.PARTIELLEMENT_RECU);
        bonCommandeMereRepo.save(bc);
        return bl;
    }

    /**
     * Arrête la réception d'un bon partiellement reçu : le reliquat ne sera pas livré.
     * Le motif est obligatoire.
     */
    @Transactional
    public void solderReliquat(String idBc, String motif, Utilisateur current) {
        BonCommandeMere bc = bonCommandeMereRepo.verrouiller(idBc)
                .orElseThrow(() -> new WorkflowException("Bon de commande introuvable."));
        if (!peutSolder(bc)) {
            throw new WorkflowException("Seul un bon de commande validé et partiellement reçu peut être soldé.");
        }
        if (motif == null || motif.isBlank()) {
            throw new WorkflowException("Le motif de la clôture est obligatoire.");
        }
        bc.setStatutReception(StatutReception.SOLDE);
        bc.setMotifClotureReception(motif.trim());
        bc.setClotureReceptionPar(current);
        bc.setDateClotureReception(LocalDateTime.now());
        bonCommandeMereRepo.save(bc);
    }

    /** Solder le reliquat n'a de sens qu'après une première réception. */
    public static boolean peutSolder(BonCommandeMere bc) {
        return bc.getStatut() == BonCommandeMere.StatutBonCommande.VALIDE
                && bc.getStatutReception() == StatutReception.PARTIELLEMENT_RECU;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record LigneRecue(BonCommandeFille ligne, Article article, double quantite, double prix,
                              double resteAvant) {
    }

    /** Un BL alimente le stock : la ligne doit porter un article actif (choisi à la réception s'il manque). */
    private Article articleDeLaLigne(BonCommandeFille ligne, String codeArticle, String libelle) {
        Article article = ligne.getArticle();
        if (article == null) {
            if (codeArticle == null || codeArticle.isBlank()) {
                throw new WorkflowException(libelle + " : choisissez l'article qui entre en stock.");
            }
            article = articleService.getArticleByCodeArticle(codeArticle.trim())
                    .orElseThrow(() -> new WorkflowException(libelle + " : article « " + codeArticle.trim() + " » introuvable."));
        }
        if (!article.estActif()) {
            throw new WorkflowException(libelle + " : l'article " + article.getCodeArticle()
                    + " est inactif, il ne peut pas entrer en stock.");
        }
        return article;
    }

    private Devise lireDevise(String deviseId) {
        if (deviseId == null || deviseId.isBlank()) {
            throw new WorkflowException("Sélectionnez la devise.");
        }
        try {
            return deviseService.getDeviseById(Integer.parseInt(deviseId.trim()))
                    .orElseThrow(() -> new WorkflowException("Devise introuvable."));
        } catch (NumberFormatException e) {
            throw new WorkflowException("Devise invalide.");
        }
    }

    private static LocalDateTime lireDate(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw new WorkflowException("La date de réception est obligatoire.");
        }
        LocalDateTime date;
        try {
            date = LocalDateTime.parse(valeur.trim());
        } catch (DateTimeParseException e) {
            throw new WorkflowException("Date de réception invalide.");
        }
        if (date.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new WorkflowException("La date de réception ne peut pas être dans le futur.");
        }
        return date;
    }

    /** Nombre positif ou nul ; vide = 0. Accepte la virgule décimale et les espaces de milliers. */
    private static double lireNombre(String valeur, String libelle) {
        if (valeur == null || valeur.isBlank()) return 0;
        double nombre;
        try {
            nombre = Double.parseDouble(valeur.replaceAll("[\\s\\u00A0\\u202F]", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new WorkflowException(libelle + " invalide.");
        }
        if (nombre < 0 || Double.isNaN(nombre) || Double.isInfinite(nombre)) {
            throw new WorkflowException(libelle + " : la valeur doit être positive.");
        }
        return nombre;
    }

    private static String numero(BonCommandeMere bc) {
        return bc.getNumero() != null && !bc.getNumero().isBlank() ? bc.getNumero() : bc.getId();
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
