package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.CategorieDepense;
import afg.achat.afgApprovAchat.model.demande.DemandeAutreDetail;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.ModeTraitement;
import afg.achat.afgApprovAchat.model.util.MontantCalculator;
import afg.achat.afgApprovAchat.model.util.TypeDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.CategorieDepenseRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeAutreDetailRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import afg.achat.afgApprovAchat.service.util.ModeTraitementService;
import afg.achat.afgApprovAchat.service.workflow.PieceJointeWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Demandes Autres : une seule dépense hors matériel (sponsoring, remboursement,
 * indemnité de repas…), réglée en paiement direct, sans article ni bon de commande.
 * Elles suivent le circuit complet (N+1, M.G., contrôleur, D.F.C., S.G.).
 */
@Service
@RequiredArgsConstructor
public class DemandeAutreService {

    private final CategorieDepenseRepo categorieDepenseRepo;
    private final DemandeAutreDetailRepo demandeAutreDetailRepo;
    private final DemandeMereRepo demandeMereRepo;
    private final ModeTraitementService modeTraitementService;
    private final DeviseService deviseService;
    private final PieceJointeWorkflowService pieceJointeService;
    private final DemandeBrouillonService demandeBrouillonService;

    /** Saisie du formulaire de création (valeurs brutes du formulaire). */
    public record NouvelleDemandeAutre(
            String categorieId,
            String beneficiaire,
            String quantite,
            String montantUnitaire,
            String deviseId,
            String datePaiement,
            String priorite,
            String motif,
            String description
    ) {}

    public List<CategorieDepense> getCategoriesActives() {
        return categorieDepenseRepo.findActives();
    }

    public Optional<DemandeAutreDetail> getDetail(DemandeMere demande) {
        return demandeAutreDetailRepo.findByDemandeMere(demande);
    }

    /**
     * Crée la demande en brouillon (avec sa dépense et ses pièces jointes), ou enregistre les
     * modifications d'un brouillon existant, en une transaction. La soumission est une étape distincte
     * ({@link DemandeBrouillonService#soumettre}).
     *
     * @param id identifiant du brouillon à modifier ; vide pour une nouvelle demande
     * @throws IllegalArgumentException si la saisie est incomplète ou invalide (rien n'est enregistré)
     */
    @Transactional
    public DemandeMere enregistrer(String id, Utilisateur demandeur, NouvelleDemandeAutre saisie,
                                   MultipartFile[] piecesJointes, Collection<Integer> piecesASupprimer) {
        DemandeMere existante = demandeBrouillonService.brouillonModifiable(id, demandeur);

        // ── 1. Contrôles (avant toute écriture) ──
        CategorieDepense categorie = lireCategorie(saisie.categorieId());

        String beneficiaire = nettoyer(saisie.beneficiaire());
        if (beneficiaire == null && categorie.estBeneficiaireObligatoire()) {
            throw new IllegalArgumentException("Le bénéficiaire est obligatoire pour une demande « " + categorie.getLibelle() + " ».");
        }

        double quantite = estVide(saisie.quantite()) ? 1.0 : MontantCalculator.parseDoubleSafe(saisie.quantite());
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantité doit être supérieure à 0.");
        }
        double montantUnitaire = MontantCalculator.parseDoubleSafe(saisie.montantUnitaire());
        if (montantUnitaire <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0.");
        }

        String motif = nettoyer(saisie.motif());
        if (motif == null) {
            throw new IllegalArgumentException("Le motif évoqué est obligatoire.");
        }

        LocalDate datePaiement = lireDate(saisie.datePaiement());
        DemandeMere.PrioriteDemande priorite = lirePriorite(saisie.priorite());
        Devise devise = deviseService.lireDevise(saisie.deviseId());

        ModeTraitement paiementDirect = modeTraitementService.getModeImpose(TypeDemande.AUTRE);
        if (paiementDirect == null) {
            throw new IllegalArgumentException("Le mode de traitement « Paiement direct » n'est pas paramétré : contactez l'administrateur.");
        }
        pieceJointeService.verifierFormats(piecesJointes);

        // ── 2. Écritures ──
        DemandeMere demande = existante != null
                ? existante
                : demandeBrouillonService.nouveauBrouillon(demandeur, TypeDemande.AUTRE);
        DemandeAutreDetail detail = existante != null
                ? demandeAutreDetailRepo.findByDemandeMere(existante).orElseGet(DemandeAutreDetail::new)
                : new DemandeAutreDetail();
        detail.setCategorie(categorie);
        detail.setBeneficiaire(beneficiaire);
        detail.definirMontant(quantite, montantUnitaire);

        demande.setDateSortie(datePaiement.atStartOfDay().toString());
        demande.setPriorite(priorite);
        demande.appliquerDevise(devise, LocalDateTime.now());
        demande.setModeTraitement(paiementDirect);
        demande.setMotifEvoque(motif);
        demande.setDescription(nettoyer(saisie.description()));
        demande.setTotalPrix(detail.getMontant());
        demande.setTotalEstime(detail.getMontant());
        demandeMereRepo.save(demande);

        detail.setDemandeMere(demande);
        demandeAutreDetailRepo.save(detail);

        demandeBrouillonService.supprimerPiecesJointes(demande, piecesASupprimer);
        pieceJointeService.enregistrerPourDemande(piecesJointes, demande, "");
        return demande;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CategorieDepense lireCategorie(String categorieId) {
        if (estVide(categorieId)) {
            throw new IllegalArgumentException("La catégorie de la demande est obligatoire.");
        }
        CategorieDepense categorie;
        try {
            categorie = categorieDepenseRepo.findById(Integer.parseInt(categorieId.trim())).orElse(null);
        } catch (NumberFormatException e) {
            categorie = null;
        }
        if (categorie == null || !categorie.estActive()) {
            throw new IllegalArgumentException("Catégorie de demande invalide.");
        }
        return categorie;
    }

    private static LocalDate lireDate(String valeur) {
        if (estVide(valeur)) {
            throw new IllegalArgumentException("La date souhaitée de paiement est obligatoire.");
        }
        try {
            return LocalDate.parse(valeur.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date souhaitée de paiement invalide : " + valeur);
        }
    }

    private static DemandeMere.PrioriteDemande lirePriorite(String valeur) {
        if (estVide(valeur)) {
            throw new IllegalArgumentException("La priorité est obligatoire.");
        }
        try {
            return DemandeMere.PrioriteDemande.valueOf(valeur.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Priorité invalide : " + valeur);
        }
    }

    private static boolean estVide(String s) {
        return s == null || s.isBlank();
    }

    private static String nettoyer(String s) {
        return estVide(s) ? null : s.trim();
    }
}
