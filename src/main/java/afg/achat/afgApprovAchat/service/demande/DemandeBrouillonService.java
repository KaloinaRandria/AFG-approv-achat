package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandePieceJointe;
import afg.achat.afgApprovAchat.model.util.MontantCalculator;
import afg.achat.afgApprovAchat.model.util.PrixArticle;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.util.TypeDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.DemandeAutreDetailRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandePieceJointeRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.util.ModeTraitementService;
import afg.achat.afgApprovAchat.service.util.PrixArticleService;
import afg.achat.afgApprovAchat.service.workflow.PieceJointeWorkflowService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Cycle brouillon → soumise des demandes (matériel et Autres), comme pour les bons de commande.
 *
 * <p>Une demande naît en {@link StatutDemande#BROUILLON} : seul son créateur la voit, la modifie
 * (informations, lignes, pièces jointes) ou la supprime. La soumission la fait entrer dans le
 * circuit de validation ({@link StatutDemande#CREE}, en attente du N+1) ; elle n'est alors plus
 * modifiable par le demandeur.</p>
 */
@Service
@RequiredArgsConstructor
public class DemandeBrouillonService {

    private final DemandeMereRepo demandeMereRepo;
    private final DemandeFilleRepo demandeFilleRepo;
    private final DemandeAutreDetailRepo demandeAutreDetailRepo;
    private final DemandePieceJointeRepo demandePieceJointeRepo;
    private final ArticleService articleService;
    private final PrixArticleService prixArticleService;
    private final ModeTraitementService modeTraitementService;
    private final DeviseService deviseService;
    private final IdGenerator idGenerator;
    private final PieceJointeWorkflowService pieceJointeService;

    /** Saisie du formulaire de demande de matériel (valeurs brutes). */
    public record SaisieMateriel(
            String dateSortie,
            String priorite,
            String motif,
            String description,
            List<String> articleCodes,
            List<String> quantites
    ) {}

    // ── Droits ───────────────────────────────────────────────────────────────

    /** Modifier, soumettre ou supprimer : un brouillon, par son créateur uniquement. */
    public static boolean peutModifier(DemandeMere demande, Utilisateur utilisateur) {
        return demande != null && demande.estBrouillon() && demande.estDemandeur(utilisateur);
    }

    /**
     * Brouillon à modifier : {@code null} pour une nouvelle demande.
     *
     * @throws IllegalArgumentException si la demande n'existe pas ou n'est plus modifiable par l'utilisateur
     */
    public DemandeMere brouillonModifiable(String id, Utilisateur utilisateur) {
        if (id == null || id.isBlank()) return null;
        DemandeMere demande = demandeMereRepo.findById(id.trim())
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));
        if (!peutModifier(demande, utilisateur)) {
            throw new IllegalArgumentException("La demande " + demande.getId()
                    + " a déjà été soumise : elle ne peut plus être modifiée.");
        }
        return demande;
    }

    // ── Demande de matériel ──────────────────────────────────────────────────

    /**
     * Crée la demande de matériel en brouillon, ou enregistre les modifications d'un brouillon existant
     * (les lignes sont remplacées par celles saisies).
     *
     * @param id identifiant du brouillon à modifier ; vide pour une nouvelle demande
     * @throws IllegalArgumentException si la saisie est incomplète ou invalide (rien n'est enregistré)
     */
    @Transactional
    public DemandeMere enregistrerMateriel(String id, Utilisateur demandeur, SaisieMateriel saisie,
                                           MultipartFile[] piecesJointes, Collection<Integer> piecesASupprimer) {
        DemandeMere existante = brouillonModifiable(id, demandeur);

        // ── 1. Contrôles (avant toute écriture) ──
        LocalDate dateSortie = lireDate(saisie.dateSortie(), "La date prévue pour la livraison est obligatoire.",
                "Date prévue pour la livraison invalide : ");
        String motif = nettoyer(saisie.motif());
        if (motif == null) {
            throw new IllegalArgumentException("Le motif evoqué est obligatoire.");
        }
        DemandeMere.PrioriteDemande priorite = lirePriorite(saisie.priorite());

        List<String> codes = saisie.articleCodes() == null ? List.of() : saisie.articleCodes();
        List<String> quantites = saisie.quantites() == null ? List.of() : saisie.quantites();
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("Impossible d'enregistrer la demande sans aucune ligne d'article.");
        }
        List<DemandeFille> lignes = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            double qte = i < quantites.size() ? MontantCalculator.parseDoubleSafe(quantites.get(i)) : 0;
            if (estVide(code) || qte <= 0) continue;
            Article article = articleActif(code.trim());

            DemandeFille ligne = new DemandeFille();
            ligne.setArticle(article);
            ligne.setQuantite(String.valueOf(qte));
            ligne.setStatut(1);
            lignes.add(ligne);
        }
        if (lignes.isEmpty()) {
            throw new IllegalArgumentException("Impossible d'enregistrer la demande : aucune ligne n'a une quantité supérieure à 0.");
        }
        pieceJointeService.verifierFormats(piecesJointes);

        // ── 2. Écritures ──
        DemandeMere demande = existante != null ? existante : nouveauBrouillon(demandeur, TypeDemande.FOURNITURE);
        demande.setDateSortie(dateSortie.atStartOfDay().toString());
        demande.setPriorite(priorite);
        demande.setMotifEvoque(motif);
        demande.setDescription(nettoyer(saisie.description()));
        // Le type impose le mode de traitement (matériel → M.G. avec bon de commande)
        demande.setModeTraitement(modeTraitementService.getModeImpose(TypeDemande.FOURNITURE));
        demandeMereRepo.save(demande);

        if (existante != null) {
            demandeFilleRepo.deleteAll(demandeFilleRepo.findDemandeFilleByDemandeMere(demande));
            demandeFilleRepo.flush();
        }
        for (DemandeFille ligne : lignes) {
            ligne.setDemandeMere(demande);
        }
        demandeFilleRepo.saveAll(lignes);
        appliquerDerniersPrix(demande, lignes);

        supprimerPiecesJointes(demande, piecesASupprimer);
        pieceJointeService.enregistrerPourDemande(piecesJointes, demande, "");
        return demande;
    }

    // ── Soumission ───────────────────────────────────────────────────────────

    /**
     * Le créateur soumet son brouillon : la demande entre dans le circuit, en attente du N+1.
     * Les données sont recontrôlées (articles toujours actifs) et les prix et cours actualisés.
     * Les notifications sont à envoyer par l'appelant, après le commit.
     *
     * @throws WorkflowException si la demande n'est pas un brouillon de l'utilisateur ou est incomplète
     */
    @Transactional
    public DemandeMere soumettre(String id, Utilisateur utilisateur) {
        DemandeMere demande = demandeMereRepo.findById(id)
                .orElseThrow(() -> new WorkflowException("Demande introuvable : " + id));
        if (!peutModifier(demande, utilisateur)) {
            throw new WorkflowException("Seul le créateur d'une demande en brouillon peut la soumettre.");
        }

        if (demande.getTypeDemandeEffectif().isDepenseAutre()) {
            if (demandeAutreDetailRepo.findByDemandeMere(demande).isEmpty()) {
                throw new WorkflowException("Impossible de soumettre la demande : la dépense n'est pas renseignée.");
            }
        } else {
            List<DemandeFille> lignes = demandeFilleRepo.findDemandeFilleByDemandeMere(demande);
            if (lignes.isEmpty()) {
                throw new WorkflowException("Impossible de soumettre une demande sans ligne d'article.");
            }
            for (DemandeFille ligne : lignes) {
                if (ligne.getArticle() == null || !ligne.getArticle().estActif()) {
                    throw new WorkflowException("L'article " + (ligne.getArticle() == null ? "?" : ligne.getArticle().getCodeArticle())
                            + " est inactif : retirez-le de la demande avant de la soumettre.");
                }
            }
            // Prix proposés au jour de la soumission
            appliquerDerniersPrix(demande, lignes);
        }

        LocalDateTime maintenant = LocalDateTime.now();
        // Cours du jour de la soumission (la devise choisie reste la même)
        demande.appliquerDevise(demande.getDevise() != null ? demande.getDevise() : deviseService.getDeviseReference(), maintenant);
        // Date de la demande = date de soumission (point de départ des délais de validation)
        demande.setDateDemande(maintenant.toString());
        demande.setStatut(StatutDemande.CREE);
        return demandeMereRepo.save(demande);
    }

    // ── Suppression ──────────────────────────────────────────────────────────

    /** Le créateur abandonne son brouillon : la demande et tout ce qui s'y rattache sont supprimés. */
    @Transactional
    public void supprimer(String id, Utilisateur utilisateur) {
        DemandeMere demande = demandeMereRepo.findById(id)
                .orElseThrow(() -> new WorkflowException("Demande introuvable : " + id));
        if (!peutModifier(demande, utilisateur)) {
            throw new WorkflowException("Seul le créateur d'une demande en brouillon peut la supprimer.");
        }
        demandePieceJointeRepo.deleteAll(demandePieceJointeRepo.findByDemande_IdOrderByUploadedAtDesc(demande.getId()));
        demandeFilleRepo.deleteAll(demandeFilleRepo.findDemandeFilleByDemandeMere(demande));
        demandeAutreDetailRepo.findByDemandeMere(demande).ifPresent(demandeAutreDetailRepo::delete);
        demandeMereRepo.delete(demande);
    }

    // ── Outils partagés avec les demandes Autres ─────────────────────────────

    /** Nouvelle demande en brouillon, en ariary par défaut (identifiant attribué dès l'enregistrement). */
    DemandeMere nouveauBrouillon(Utilisateur demandeur, TypeDemande type) {
        DemandeMere demande = new DemandeMere();
        demande.setId(idGenerator);
        demande.setDateDemande(String.valueOf(LocalDateTime.now()));
        demande.setTypeDemande(type);
        demande.setDemandeur(demandeur);
        demande.setStatut(StatutDemande.BROUILLON);
        demande.appliquerDevise(deviseService.getDeviseReference(), LocalDateTime.now());
        return demande;
    }

    /** Retire du brouillon les pièces jointes cochées (uniquement celles de cette demande). */
    void supprimerPiecesJointes(DemandeMere demande, Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<DemandePieceJointe> aSupprimer = demandePieceJointeRepo.findByDemande_IdOrderByUploadedAtDesc(demande.getId())
                .stream()
                .filter(pj -> ids.contains(pj.getId()))
                .toList();
        demandePieceJointeRepo.deleteAll(aSupprimer);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Prix proposés : dernier prix d'achat de chaque article (en ariary, devise d'une demande de matériel). */
    private void appliquerDerniersPrix(DemandeMere demande, List<DemandeFille> lignes) {
        double total = 0.0;
        for (DemandeFille ligne : lignes) {
            double prix = prixArticleService.getDernierPrixByArticle(ligne.getArticle().getCodeArticle())
                    .map(PrixArticle::getPrixUnitaire)
                    .orElse(0.0);
            ligne.setPrixUnitaire(prix);
            total += ligne.getQuantite() * prix;
        }
        demandeFilleRepo.saveAll(lignes);
        demande.setTotalPrix(total);
        demande.setTotalEstime(total);
        demandeMereRepo.save(demande);
    }

    private Article articleActif(String code) {
        Article article = articleService.getArticleByCodeArticle(code)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable : " + code));
        if (!article.estActif()) {
            throw new IllegalArgumentException("L'article " + code + " est inactif : il ne peut plus être demandé.");
        }
        return article;
    }

    static LocalDate lireDate(String valeur, String messageObligatoire, String messageInvalide) {
        if (estVide(valeur)) {
            throw new IllegalArgumentException(messageObligatoire);
        }
        try {
            return LocalDate.parse(valeur.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(messageInvalide + valeur);
        }
    }

    static DemandeMere.PrioriteDemande lirePriorite(String valeur) {
        if (estVide(valeur)) {
            throw new IllegalArgumentException("La priorité est obligatoire.");
        }
        try {
            return DemandeMere.PrioriteDemande.valueOf(valeur.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Priorité invalide : " + valeur);
        }
    }

    static boolean estVide(String s) {
        return s == null || s.isBlank();
    }

    static String nettoyer(String s) {
        return estVide(s) ? null : s.trim();
    }
}
