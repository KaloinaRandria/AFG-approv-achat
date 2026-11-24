package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.EtapeValidation;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.util.TypeDemande;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableFournisseurService;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.service.util.DeviseService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bon de commande créé manuellement (sans demande d'achat) et son circuit de validation.
 *
 * <p>Circuit : un créateur (rôle MG_CREATOR ou Moyens Généraux) saisit le bon en brouillon et le soumet ;
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
    private final DemandeFilleService demandeFilleService;
    private final BesoinAchatService besoinAchatService;
    private final IdGenerator idGenerator;
    private final DeviseService deviseService;

    // ── Droits ───────────────────────────────────────────────────────────────

    /** Créer un bon manuel : rôle MG_CREATOR, Moyens Généraux ou administrateur. */
    public static boolean peutCreer(Authentication auth) {
        return WorkflowService.hasRole(auth, ROLE_CREATEUR)
                || WorkflowService.hasRole(auth, "ROLE_MOYENS_GENERAUX")
                || WorkflowService.hasRole(auth, ROLE_ADMIN);
    }

    /** Modifier avec le formulaire du bon manuel : un bon manuel encore en brouillon, par un créateur. */
    public static boolean peutModifier(BonCommandeMere bc, Authentication auth) {
        return bc.estManuel() && bc.getStatut() == StatutBonCommande.BROUILLON && peutCreer(auth);
    }

    /**
     * Soumettre à validation ou annuler un brouillon : bon manuel (créateur) ou bon issu
     * d'une demande (Moyens Généraux). Les deux suivent le même circuit de validation.
     */
    public static boolean peutSoumettre(BonCommandeMere bc, Authentication auth) {
        if (bc.getStatut() != StatutBonCommande.BROUILLON) return false;
        return bc.estManuel() ? peutCreer(auth) : BonCommandeDemandeService.peutGerer(auth);
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
        Devise devise;
        try {
            devise = deviseService.lireDevise(saisie.deviseId());
        } catch (IllegalArgumentException e) {
            throw new WorkflowException(e.getMessage());
        }
        String codeDevise = devise == null || devise.getAcronyme() == null ? Devise.CODE_REFERENCE : devise.getAcronyme();
        List<BonCommandeFille> lignes = lireLignes(saisie.lignes(), saisie.id(), codeDevise);

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
        // Cours du jour recopié à chaque enregistrement du brouillon, figé une fois le bon soumis
        bc.appliquerDevise(devise, LocalDateTime.now());

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
        if (!peutSoumettre(bc, auth)) {
            throw new WorkflowException("Ce bon de commande ne peut pas être soumis par vous.");
        }
        if (bc.getLignes().isEmpty()) {
            throw new WorkflowException("Impossible de soumettre un bon de commande sans ligne.");
        }
        if (bc.getFournisseur() == null) {
            throw new WorkflowException("Choisissez le fournisseur avant de soumettre le bon de commande.");
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
        if (!peutSoumettre(bc, auth)) {
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
    /**
     * Lignes du formulaire, sans les lignes vides ; au moins une ligne est exigée.
     * Une ligne liée à une ligne de demande prend l'article de la demande, et la quantité
     * totale commandée pour cette ligne ne peut pas dépasser son reste à commander.
     *
     * @param bcEnCours  identifiant du bon modifié ; {@code null} ou vide pour un nouveau bon
     * @param codeDevise devise du bon : chaque ligne de demande doit être dans cette devise
     */
    private List<BonCommandeFille> lireLignes(List<SaisieBonCommande.Ligne> saisies, String bcEnCours,
                                              String codeDevise) {
        List<BonCommandeFille> lignes = new ArrayList<>();
        // Quantité totale commandée dans ce bon par ligne de demande (une ligne peut être répétée)
        Map<DemandeFille, Double> quantiteParLigneDemande = new LinkedHashMap<>();
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

            DemandeFille ligneDemande = lireLigneDemande(saisie.demandeFilleId(), numero);
            Article article = null;
            if (ligneDemande != null) {
                // Un bon ne mélange pas les devises : ses prix viennent des prix de la demande
                String deviseDemande = ligneDemande.getDemandeMere().getCodeDevise();
                if (!deviseDemande.equalsIgnoreCase(codeDevise)) {
                    throw new WorkflowException("Ligne " + numero + " : la demande " + ligneDemande.getDemandeMere().getId()
                            + " est en " + deviseDemande + ", le bon de commande en " + codeDevise
                            + ". Un bon de commande ne regroupe que des lignes de même devise.");
                }
                // L'article est celui de la ligne de demande, quel que soit le code saisi
                article = ligneDemande.getArticle();
                quantiteParLigneDemande.merge(ligneDemande, quantite, Double::sum);
            } else if (saisie.codeArticle() != null && !saisie.codeArticle().isBlank()) {
                String code = saisie.codeArticle().trim();
                int n = numero;
                article = articleService.getArticleByCodeArticle(code)
                        .orElseThrow(() -> new WorkflowException("Ligne " + n + " : article introuvable (" + code + ")."));
            }

            BonCommandeFille ligne = new BonCommandeFille();
            ligne.setDesignation(saisie.designation().trim());
            ligne.setArticle(article);
            ligne.setDemandeFille(ligneDemande);
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

        // Pas de commande au-delà du besoin : une ligne de demande n'est commandée qu'une fois
        for (Map.Entry<DemandeFille, Double> e : quantiteParLigneDemande.entrySet()) {
            DemandeFille df = e.getKey();
            double reste = besoinAchatService.resteACommander(df, bcEnCours);
            if (e.getValue() > reste + 0.001) {
                throw new WorkflowException("Demande " + df.getDemandeMere().getId() + ", "
                        + libelle(df) + " : " + formater(e.getValue()) + " commandé(s), mais il ne reste que "
                        + formater(reste) + " à commander (le reste est déjà sorti du stock ou commandé).");
            }
        }
        return lignes;
    }

    /**
     * Ligne de demande liée, facultative : elle doit appartenir à une demande de matériel
     * validée et être achetable (mêmes règles que l'écran « Lignes à acheter »).
     */
    private DemandeFille lireLigneDemande(String demandeFilleId, int numero) {
        if (demandeFilleId == null || demandeFilleId.isBlank()) return null;
        DemandeFille df;
        try {
            df = demandeFilleService.getDemandeFilleById(Integer.parseInt(demandeFilleId.trim()));
        } catch (NumberFormatException e) {
            throw new WorkflowException("Ligne " + numero + " : ligne de demande invalide.");
        }
        if (df == null || df.getDemandeMere() == null) {
            throw new WorkflowException("Ligne " + numero + " : ligne de demande introuvable.");
        }
        if (df.getDemandeMere().getTypeDemande() != TypeDemande.FOURNITURE) {
            throw new WorkflowException("Ligne " + numero + " : la demande " + df.getDemandeMere().getId()
                    + " n'est pas une demande de matériel.");
        }
        if (df.getDemandeMere().getStatut() != StatutDemande.VALIDE || df.getStatut() == StatutDemande.REFUSE
                || df.getTypeApprovisionnement() == DemandeFille.TypeApprovisionnement.STOCK) {
            throw new WorkflowException("Ligne " + numero + " : la ligne de la demande " + df.getDemandeMere().getId()
                    + " n'est pas à acheter (demande non validée, ligne refusée ou servie depuis le stock).");
        }
        return df;
    }

    private static String libelle(DemandeFille df) {
        return df.getArticle() != null
                ? df.getArticle().getCodeArticle() + " - " + df.getArticle().getDesignation()
                : "ligne " + df.getId();
    }

    private static String formater(double quantite) {
        return quantite == Math.rint(quantite) ? String.valueOf((long) quantite) : String.valueOf(quantite);
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

    /**
     * Texte saisi, sans espaces superflus. Les colonnes texte font 255 caractères : au-delà,
     * l'enregistrement échouerait en base. L'exception annule toute la transaction.
     */
    private static String nettoyer(String valeur) {
        String texte = valeur == null ? "" : valeur.trim();
        if (texte.length() > 255) {
            throw new WorkflowException("Un champ dépasse 255 caractères : « "
                    + texte.substring(0, 30) + "… ». Raccourcissez-le.");
        }
        return texte;
    }
}
