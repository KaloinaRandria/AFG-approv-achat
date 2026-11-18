package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.EtapeValidation;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableFournisseurService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import afg.achat.afgApprovAchat.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bon de commande créé manuellement (sans demande d'achat) et son circuit de validation.
 *
 * <p>Circuit : un créateur (rôle MG_CREATOR) saisit le bon en brouillon et le soumet ;
 * les Moyens Généraux (rôle MG_VALIDATOR) le valident, puis le S.G. (rôle SG) donne la
 * validation finale. Un rejet, à l'une ou l'autre étape, renvoie le bon en brouillon
 * avec son motif. L'administrateur peut agir à chaque étape.</p>
 *
 * <p>Chaque action fait tous ses contrôles avant la première écriture ; une
 * {@link WorkflowException} porte le message à afficher.</p>
 */
@Service
@RequiredArgsConstructor
public class BonCommandeManuelService {

    public static final String ROLE_CREATEUR = "ROLE_MG_CREATOR";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final BonCommandeMereRepo bonCommandeMereRepo;
    private final FournisseurService fournisseurService;
    private final AdresseFournisseurService adresseFournisseurService;
    private final ResponsableFournisseurService responsableFournisseurService;
    private final ArticleService articleService;
    private final IdGenerator idGenerator;

    // ── Droits ───────────────────────────────────────────────────────────────

    public static boolean peutCreer(Authentication auth) {
        return WorkflowService.hasRole(auth, ROLE_CREATEUR) || WorkflowService.hasRole(auth, ROLE_ADMIN);
    }

    /** Modifier, soumettre ou annuler : un bon manuel encore en brouillon, par un créateur. */
    public static boolean peutModifier(BonCommandeMere bc, Authentication auth) {
        return bc.estManuel() && bc.getStatut() == StatutBonCommande.BROUILLON && peutCreer(auth);
    }

    /** Valider ou rejeter : le bon attend une validation et l'utilisateur a le rôle de cette étape. */
    public static boolean peutValider(BonCommandeMere bc, Authentication auth) {
        if (bc.getStatut() != StatutBonCommande.EN_ATTENTE_VALIDATION || bc.getEtapeValidation() == null) {
            return false;
        }
        return WorkflowService.hasRole(auth, "ROLE_" + bc.getEtapeValidation().getRole())
                || WorkflowService.hasRole(auth, ROLE_ADMIN);
    }

    // ── Saisie ───────────────────────────────────────────────────────────────

    /** Crée le bon en brouillon, ou enregistre les modifications d'un brouillon existant. */
    @Transactional
    public BonCommandeMere enregistrer(SaisieBonCommande saisie, Utilisateur current, Authentication auth) {
        if (!peutCreer(auth)) {
            throw new WorkflowException("Vous ne pouvez pas créer de bon de commande.");
        }

        // ── 1. Contrôles ──
        BonCommandeMere bc = null;
        if (saisie.id() != null && !saisie.id().isBlank()) {
            bc = charger(saisie.id());
            if (!peutModifier(bc, auth)) {
                throw new WorkflowException("Ce bon de commande ne peut plus être modifié.");
            }
        }
        Fournisseur fournisseur = lireFournisseur(saisie.fournisseurId());
        LocalDateTime dateLivraison = lireDate(saisie.dateLivraisonPrevue());
        double tauxRemise = lireTaux(saisie.tauxRemise(), "remise");
        double tauxTVA = lireTaux(saisie.tauxTVA(), "TVA");
        List<BonCommandeFille> lignes = lireLignes(saisie.lignes());

        // ── 2. Écritures ──
        if (bc == null) {
            bc = new BonCommandeMere();
            bc.setId(idGenerator);
            bc.setNumero(idGenerator.generateNumeroBC());
            bc.setDateCreation(LocalDateTime.now());
            bc.setCreateur(current);
            bc.setStatut(StatutBonCommande.BROUILLON);
        }
        bc.setFournisseur(fournisseur);
        // Adresse et contact du fournisseur au moment de la saisie
        bc.setAdresseFournisseur(adresseFournisseurService.getAdresseActuel(fournisseur.getId()).orElse(null));
        bc.setResponsableFournisseur(responsableFournisseurService.getResponsableActuel(fournisseur.getId()).orElse(null));
        bc.setReferenceFournisseur(nettoyer(saisie.referenceFournisseur()));
        bc.setDateLivraisonPrevue(dateLivraison);
        bc.setLieuLivraison(nettoyer(saisie.lieuLivraison()));
        bc.setModalitePaiement(nettoyer(saisie.modalitePaiement()));
        bc.setReferenceApprobation(nettoyer(saisie.referenceApprobation()));
        bc.setDescription(nettoyer(saisie.description()));

        double montantHT = lignes.stream().mapToDouble(BonCommandeFille::getMontantHT).sum();
        double montantHTRemise = montantHT * (1 - tauxRemise / 100);
        bc.setMontantHT(montantHT);
        bc.setRemise(tauxRemise);
        bc.setTauxTVA(tauxTVA);
        bc.setMontantTTC(montantHTRemise * (1 + tauxTVA / 100));

        // Les lignes sont remplacées en bloc (orphanRemoval supprime les anciennes)
        bc.getLignes().clear();
        for (BonCommandeFille ligne : lignes) {
            ligne.setBonCommandeMere(bc);
            bc.getLignes().add(ligne);
        }
        return bonCommandeMereRepo.save(bc);
    }

    // ── Circuit de validation ────────────────────────────────────────────────

    /** Le créateur envoie le brouillon à la validation des Moyens Généraux. */
    @Transactional
    public void soumettre(String id, Utilisateur current, Authentication auth) {
        BonCommandeMere bc = charger(id);
        if (!peutModifier(bc, auth)) {
            throw new WorkflowException("Ce bon de commande ne peut pas être soumis par vous.");
        }
        if (bc.getLignes().isEmpty()) {
            throw new WorkflowException("Impossible de soumettre un bon de commande sans ligne.");
        }

        bc.setStatut(StatutBonCommande.EN_ATTENTE_VALIDATION);
        bc.setEtapeValidation(EtapeValidation.MG);
        bc.setSoumisPar(current);
        bc.setDateSoumission(LocalDateTime.now());
        // Nouvelle soumission : les validations et le rejet précédents ne valent plus
        bc.setValideMgPar(null);
        bc.setDateValidationMg(null);
        bc.setValideSgPar(null);
        bc.setDateValidationSg(null);
        bc.setMotifRejet(null);
        bc.setRejetePar(null);
        bonCommandeMereRepo.save(bc);
    }

    /** Valide l'étape en cours ; renvoie le message de confirmation. */
    @Transactional
    public String valider(String id, Utilisateur current, Authentication auth) {
        BonCommandeMere bc = charger(id);
        if (!peutValider(bc, auth)) {
            throw new WorkflowException("Ce bon de commande ne peut pas être validé par vous.");
        }

        String message;
        if (bc.getEtapeValidation() == EtapeValidation.MG) {
            bc.setValideMgPar(current);
            bc.setDateValidationMg(LocalDateTime.now());
            bc.setEtapeValidation(EtapeValidation.SG);
            message = "Bon de commande validé par les Moyens Généraux, transmis au S.G.";
        } else {
            bc.setValideSgPar(current);
            bc.setDateValidationSg(LocalDateTime.now());
            bc.setEtapeValidation(null);
            bc.setStatut(StatutBonCommande.VALIDE);
            message = "Bon de commande validé par le S.G.";
        }
        bonCommandeMereRepo.save(bc);
        return message;
    }

    /** Rejette le bon à l'étape en cours : il revient en brouillon avec le motif. */
    @Transactional
    public void rejeter(String id, Utilisateur current, Authentication auth, String motif) {
        BonCommandeMere bc = charger(id);
        if (!peutValider(bc, auth)) {
            throw new WorkflowException("Ce bon de commande ne peut pas être rejeté par vous.");
        }
        if (motif == null || motif.isBlank()) {
            throw new WorkflowException("Le motif est obligatoire pour rejeter un bon de commande.");
        }

        bc.setStatut(StatutBonCommande.BROUILLON);
        bc.setEtapeValidation(null);
        bc.setMotifRejet(motif.trim());
        bc.setRejetePar(current);
        bonCommandeMereRepo.save(bc);
    }

    /** Le créateur abandonne un brouillon. */
    @Transactional
    public void annuler(String id, Authentication auth) {
        BonCommandeMere bc = charger(id);
        if (!peutModifier(bc, auth)) {
            throw new WorkflowException("Ce bon de commande ne peut pas être annulé par vous.");
        }
        bc.setStatut(StatutBonCommande.ANNULE);
        bonCommandeMereRepo.save(bc);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BonCommandeMere charger(String id) {
        return bonCommandeMereRepo.findById(id)
                .orElseThrow(() -> new WorkflowException("Bon de commande introuvable."));
    }

    private Fournisseur lireFournisseur(String fournisseurId) {
        if (fournisseurId == null || fournisseurId.isBlank()) {
            throw new WorkflowException("Le fournisseur est obligatoire.");
        }
        try {
            return fournisseurService.getFournisseurById(Integer.parseInt(fournisseurId.trim()))
                    .orElseThrow(() -> new WorkflowException("Fournisseur introuvable."));
        } catch (NumberFormatException e) {
            throw new WorkflowException("Fournisseur invalide.");
        }
    }

    /** Date de livraison facultative. */
    private static LocalDateTime lireDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new WorkflowException("Date de livraison invalide.");
        }
    }

    private static double lireTaux(Double taux, String nom) {
        if (taux == null) return 0;
        if (taux < 0 || taux > 100) {
            throw new WorkflowException("Le taux de " + nom + " doit être compris entre 0 et 100.");
        }
        return taux;
    }

    /** Lignes du formulaire, sans les lignes vides ; au moins une ligne est exigée. */
    private List<BonCommandeFille> lireLignes(List<SaisieBonCommande.Ligne> saisies) {
        List<BonCommandeFille> lignes = new ArrayList<>();
        int numero = 0;
        for (SaisieBonCommande.Ligne saisie : saisies == null ? List.<SaisieBonCommande.Ligne>of() : saisies) {
            if (saisie.estVide()) continue;
            numero++;

            if (saisie.designation() == null || saisie.designation().isBlank()) {
                throw new WorkflowException("Ligne " + numero + " : la désignation est obligatoire.");
            }
            double quantite = lireNombre(saisie.quantite(), "Ligne " + numero + " : quantité");
            if (quantite <= 0) {
                throw new WorkflowException("Ligne " + numero + " : la quantité doit être supérieure à 0.");
            }
            double prix = lireNombre(saisie.prixUnitaire(), "Ligne " + numero + " : prix unitaire");

            Article article = null;
            if (saisie.codeArticle() != null && !saisie.codeArticle().isBlank()) {
                String code = saisie.codeArticle().trim();
                int n = numero;
                article = articleService.getArticleByCodeArticle(code)
                        .orElseThrow(() -> new WorkflowException("Ligne " + n + " : article introuvable (" + code + ")."));
            }

            BonCommandeFille ligne = new BonCommandeFille();
            ligne.setDesignation(saisie.designation().trim());
            ligne.setArticle(article);
            ligne.setUnite(nettoyer(saisie.unite()));
            ligne.setQuantiteCommandee(quantite);
            ligne.setQuantiteRestante(quantite);
            ligne.setPrixUnitaireHT(prix);
            ligne.setMontantHT(quantite * prix);
            lignes.add(ligne);
        }
        if (lignes.isEmpty()) {
            throw new WorkflowException("Le bon de commande doit contenir au moins une ligne.");
        }
        return lignes;
    }

    private static double lireNombre(String valeur, String nom) {
        if (valeur == null || valeur.isBlank()) {
            throw new WorkflowException(nom + " obligatoire.");
        }
        try {
            double nombre = Double.parseDouble(valeur.trim().replace(" ", "").replace(',', '.'));
            if (nombre < 0) throw new WorkflowException(nom + " invalide.");
            return nombre;
        } catch (NumberFormatException e) {
            throw new WorkflowException(nom + " invalide.");
        }
    }

    private static String nettoyer(String valeur) {
        return valeur == null ? "" : valeur.trim();
    }
}
