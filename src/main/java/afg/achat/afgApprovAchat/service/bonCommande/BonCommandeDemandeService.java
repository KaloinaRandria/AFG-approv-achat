package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.fournisseur.Adresse;
import afg.achat.afgApprovAchat.model.fournisseur.AdresseFournisseur;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.fournisseur.Responsable;
import afg.achat.afgApprovAchat.model.fournisseur.ResponsableFournisseur;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseService;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import afg.achat.afgApprovAchat.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Bon de commande créé à partir d'une demande (ancien circuit), complété par les Moyens Généraux.
 *
 * <p>La création depuis la fiche de la demande est retirée : un bon de commande regroupe
 * désormais les lignes de plusieurs demandes (écran « Lignes à acheter », bon manuel). Ce
 * service ne sert plus qu'à terminer les brouillons existants. Quantités et prix suivent les
 * lignes de la demande ; les montants sont toujours recalculés ici, jamais repris du navigateur.
 * Une fois complété, le bon suit le même circuit de validation que le bon manuel (Moyens Généraux puis S.G.), voir
 * {@link BonCommandeManuelService}.</p>
 */
@Service
@RequiredArgsConstructor
public class BonCommandeDemandeService {

    private static final int LONGUEUR_MAX = 255;

    private final BonCommandeMereRepo bonCommandeMereRepo;
    private final FournisseurService fournisseurService;
    private final ResponsableFournisseurService responsableFournisseurService;
    private final ResponsableService responsableService;
    private final AdresseFournisseurService adresseFournisseurService;
    private final AdresseService adresseService;

    // ── Droits ───────────────────────────────────────────────────────────────

    /** Créer et compléter un bon issu d'une demande : Moyens Généraux et administrateur. */
    public static boolean peutGerer(Authentication auth) {
        return WorkflowService.hasRole(auth, "ROLE_MOYENS_GENERAUX") || WorkflowService.hasRole(auth, "ROLE_ADMIN");
    }

    /** Bon issu d'une demande, encore en brouillon, modifiable par l'utilisateur. */
    public static boolean peutCompleter(BonCommandeMere bc, Authentication auth) {
        return !bc.estManuel() && bc.getStatut() == StatutBonCommande.BROUILLON && peutGerer(auth);
    }

    // ── Saisie ───────────────────────────────────────────────────────────────

    /** Enregistre les informations saisies par les Moyens Généraux sur le brouillon. */
    @Transactional
    public BonCommandeMere enregistrer(SaisieBonCommandeDemande saisie, Authentication auth) {
        // ── 1. Contrôles (avant toute écriture) ──
        BonCommandeMere bc = bonCommandeMereRepo.findById(saisie.id() == null ? "" : saisie.id())
                .orElseThrow(() -> new WorkflowException("Bon de commande introuvable."));
        if (!peutCompleter(bc, auth)) {
            throw new WorkflowException("Ce bon de commande ne peut plus être modifié.");
        }
        Fournisseur fournisseur = lireFournisseur(saisie.fournisseurId());
        LocalDateTime dateLivraison = lireDate(saisie.dateLivraisonPrevue());
        double tauxRemise = lireTaux(saisie.tauxRemise(), "remise");
        double tauxTVA = lireTaux(saisie.tauxTVA(), "TVA");
        String numero = texte(saisie.numero(), "Numéro");
        if (!numero.isEmpty() && !numero.equals(bc.getNumero())
                && bonCommandeMereRepo.existsByNumero(numero)) {
            throw new WorkflowException("Le numéro " + numero + " est déjà utilisé par un autre bon de commande.");
        }
        String referenceFournisseur = texte(saisie.referenceFournisseur(), "Référence fournisseur");
        String lieuLivraison = texte(saisie.lieuLivraison(), "Lieu de livraison");

        // ── 2. Écritures ──
        if (!numero.isEmpty()) bc.setNumero(numero);
        bc.setDateLivraisonPrevue(dateLivraison);
        bc.setReferenceFournisseur(referenceFournisseur);
        bc.setLieuLivraison(lieuLivraison);
        if (fournisseur != null) {
            bc.setFournisseur(fournisseur);
            appliquerResponsable(bc, fournisseur, saisie.responsable());
            appliquerAdresse(bc, fournisseur, saisie.adresse());
        }

        // Seule la désignation fournisseur se saisit ; quantités et prix suivent la demande
        for (BonCommandeFille ligne : bc.getLignes()) {
            String designation = saisie.designationsFournisseur() == null ? null
                    : saisie.designationsFournisseur().get(ligne.getId());
            if (designation != null) {
                ligne.setDesignationFournisseur(designation.trim());
            }
            if (ligne.getDemandeFille() != null) {
                appliquerQuantiteEtPrix(ligne, ligne.getDemandeFille());
            }
        }
        recalculerMontants(bc, tauxRemise, tauxTVA);
        return bonCommandeMereRepo.save(bc);
    }

    // ── Montants ─────────────────────────────────────────────────────────────

    private static void appliquerQuantiteEtPrix(BonCommandeFille ligne, DemandeFille df) {
        double prix = df.getPrixUnitaire() != null ? df.getPrixUnitaire() : 0;
        ligne.setQuantiteCommandee(df.getQuantite());
        ligne.setQuantiteRestante(df.getQuantite() - ligne.getQuantiteRecue());
        ligne.setPrixUnitaireHT(prix);
        ligne.setMontantHT(df.getQuantite() * prix);
    }

    /** HT = somme des lignes ; remise puis TVA en pourcentage ; TTC = HT remisé + TVA. */
    static void recalculerMontants(BonCommandeMere bc, double tauxRemise, double tauxTVA) {
        double montantHT = bc.getLignes().stream().mapToDouble(BonCommandeFille::getMontantHT).sum();
        double montantHTRemise = montantHT * (1 - tauxRemise / 100);
        bc.setMontantHT(montantHT);
        bc.setRemise(tauxRemise);
        bc.setTauxTVA(tauxTVA);
        bc.setMontantTTC(montantHTRemise * (1 + tauxTVA / 100));
    }

    // ── Fournisseur : responsable et adresse ─────────────────────────────────

    private void appliquerResponsable(BonCommandeMere bc, Fournisseur fournisseur,
                                      SaisieBonCommandeDemande.Responsable saisie) {
        if (saisie == null) return;
        boolean nouveauxChamps = saisie.nouveauxChampsRenseignes();
        Integer rfId = lireId(saisie.responsableFournisseurId());

        if ("update".equals(saisie.action()) && rfId != null && nouveauxChamps) {
            // Correction des informations de la personne déjà affectée
            ResponsableFournisseur rf = responsableFournisseurService.getById(rfId)
                    .orElseThrow(() -> new WorkflowException("Responsable du fournisseur introuvable."));
            Responsable responsable = rf.getResponsable();
            responsable.setNom(saisie.nom().trim());
            responsable.setPrenom(saisie.prenom().trim());
            if (saisie.contact() != null && !saisie.contact().isBlank()) {
                responsable.setContact(saisie.contact().trim());
            }
            responsableService.save(responsable);
            bc.setResponsableFournisseur(rf);
        } else if (nouveauxChamps && ("new".equals(saisie.action()) || rfId == null)) {
            // Nouvelle personne affectée au fournisseur (historique des affectations)
            Responsable responsable = new Responsable();
            responsable.setNom(saisie.nom().trim());
            responsable.setPrenom(saisie.prenom().trim());
            responsable.setContact(saisie.contact() != null ? saisie.contact().trim() : null);

            ResponsableFournisseur rf = new ResponsableFournisseur();
            rf.setFournisseur(fournisseur);
            rf.setResponsable(responsableService.save(responsable));
            rf.setDateAffectation(LocalDate.now());
            bc.setResponsableFournisseur(responsableFournisseurService.save(rf));
        } else if (rfId != null) {
            // Personne existante choisie, sans modification
            responsableFournisseurService.getById(rfId).ifPresent(bc::setResponsableFournisseur);
        }
    }

    private void appliquerAdresse(BonCommandeMere bc, Fournisseur fournisseur,
                                  SaisieBonCommandeDemande.Adresse saisie) {
        if (saisie == null) return;
        boolean libelleRenseigne = saisie.libelleRenseigne();
        Integer afId = lireId(saisie.adresseFournisseurId());

        if ("update".equals(saisie.action()) && afId != null && libelleRenseigne) {
            AdresseFournisseur af = adresseFournisseurService.getById(afId)
                    .orElseThrow(() -> new WorkflowException("Adresse du fournisseur introuvable."));
            af.getAdresse().setLibelle(saisie.libelle().trim());
            adresseService.save(af.getAdresse());
            bc.setAdresseFournisseur(af);
        } else if (libelleRenseigne && ("new".equals(saisie.action()) || afId == null)) {
            Adresse adresse = new Adresse();
            adresse.setLibelle(saisie.libelle().trim());

            AdresseFournisseur af = new AdresseFournisseur();
            af.setFournisseur(fournisseur);
            af.setAdresse(adresseService.save(adresse));
            af.setDateAffectation(LocalDate.now());
            bc.setAdresseFournisseur(adresseFournisseurService.save(af));
        } else if (afId != null) {
            adresseFournisseurService.getById(afId).ifPresent(bc::setAdresseFournisseur);
        }
    }

    // ── Lecture des champs ───────────────────────────────────────────────────

    /** Fournisseur facultatif tant que le bon est en brouillon (exigé à la soumission). */
    private Fournisseur lireFournisseur(String fournisseurId) {
        Integer id = lireId(fournisseurId);
        if (id == null) return null;
        return fournisseurService.getFournisseurById(id)
                .orElseThrow(() -> new WorkflowException("Fournisseur introuvable."));
    }

    private static Integer lireId(String valeur) {
        if (valeur == null || valeur.isBlank()) return null;
        try {
            return Integer.parseInt(valeur.trim());
        } catch (NumberFormatException e) {
            throw new WorkflowException("Identifiant invalide : " + valeur);
        }
    }

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

    private static String texte(String valeur, String nom) {
        String t = valeur == null ? "" : valeur.trim();
        if (t.length() > LONGUEUR_MAX) {
            throw new WorkflowException(nom + " : " + LONGUEUR_MAX + " caractères au maximum.");
        }
        return t;
    }
}
