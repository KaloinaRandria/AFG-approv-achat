package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.DTO.FournisseurAutocompleteDTO;
import afg.achat.afgApprovAchat.model.fournisseur.*;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.fournisseur.*;
import afg.achat.afgApprovAchat.service.bonCommande.BcContactService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@PreAuthorize("hasAnyRole('ADMIN', 'MOYENS_GENERAUX')")
@Controller
@RequestMapping("/bon-commande")
@RequiredArgsConstructor
public class BonCommandeController {
    private final BonCommandeService bonCommandeService;
    private final DemandeMereService demandeMereService;
    private final DemandeFilleService demandeFilleService;
    private final FournisseurService fournisseurService;
    private final UtilisateurService utilisateurService;
    private final BcContactService bcContactService;
    private final ResponsableFournisseurService responsableFournisseurService;
    private final AdresseFournisseurService adresseFournisseurService;
    private final AdresseService adresseService;
    private final ResponsableService responsableService;
    private final IdGenerator idGenerator;

    @GetMapping("")
    public String goToBonCommandePage() {
        //redirection vers la fiche du bon de commande dans une demande validee
        return "";
    }

    @GetMapping("/list")
    public String bonCommandeList() {
        return "";
    }
    @GetMapping("/creer/{id}")
    public String creeBonCommandeByDemande(@PathVariable(name = "id") String demandeMereId,
                                           RedirectAttributes redirectAttributes) {
        DemandeMere demandeMere = demandeMereService.getDemandeById(demandeMereId);
        List<BonCommandeMere> bcExistants = bonCommandeService.getBonCommandesByDemande(demandeMere);
        if (!bcExistants.isEmpty()) {
            BonCommandeMere bcExistant = bcExistants.get(0);
            redirectAttributes.addFlashAttribute("warningMessage",
                    "Un bon de commande existe deja pour cette demande.");
            return "redirect:/bon-commande/" + bcExistant.getId();
        }

        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(user.getMail());
        BonCommandeMere bonCommandeMere = initialiserBonCommandeDepuisDemande(demandeMere, utilisateur);

        BonCommandeMere bcSauvegardee = bonCommandeService.saveBonCommandeMere(bonCommandeMere);
        List<BonCommandeFille> bonCommandeFilles = initialiserLignesBonCommande(demandeMere, bcSauvegardee);
        for (BonCommandeFille bcFille : bonCommandeFilles) {
            bonCommandeService.saveBonCommandeFille(bcFille);
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Bon de commande cree en brouillon. Vous pouvez completer les informations.");
        return "redirect:/bon-commande/" + bcSauvegardee.getId();
    }

    @PostMapping("/sauvegarder")
    public String sauvegarderBonCommande(
            @RequestParam(required = false) String idBcMere,
            @RequestParam String demandeMereId,
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) String dateLivraisonPrevue,
            @RequestParam(name = "fournisseurs", required = false) String fournisseurId,
            @RequestParam(required = false) String actionResponsable, // "update", "new" ou vide
            @RequestParam(required = false) String responsableFournisseurId,
            @RequestParam(required = false) String nouveauResponsableNom,
            @RequestParam(required = false) String nouveauResponsablePrenom,
            @RequestParam(required = false) String nouveauResponsableContact,
            @RequestParam(required = false) String referenceFournisseur,
            @RequestParam(required = false) String lieuLivraison,
            @RequestParam(required = false) Double montantHT,
            @RequestParam(required = false) Double montantTTC,
            @RequestParam(required = false) Double tauxTVA,
            @RequestParam(required = false) Double remise,
            @RequestParam(required = false) String actionAdresse, // "update", "new" ou vide
            @RequestParam(required = false) String adresseFournisseurId,
            @RequestParam(required = false) String nouvelleAdresseLibelle,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(user.getMail());

            // Récupérer ou créer le BC
            BonCommandeMere bonCommandeMere;
            if (idBcMere == null || idBcMere.isEmpty()) {
                bonCommandeMere = new BonCommandeMere();
                bonCommandeMere.setId(idGenerator);
                bonCommandeMere.setDateCreation(LocalDateTime.now());
                bonCommandeMere.setCreateur(utilisateur);
                bonCommandeMere.setStatut(BonCommandeMere.StatutBonCommande.BROUILLON);
            } else {
                bonCommandeMere = bonCommandeService.getBonCommandeMereById(idBcMere)
                        .orElse(new BonCommandeMere());
                if (bonCommandeMere.getId() == null) {
                    bonCommandeMere.setId(idGenerator);
                    bonCommandeMere.setDateCreation(LocalDateTime.now());
                    bonCommandeMere.setCreateur(utilisateur);
                    bonCommandeMere.setStatut(BonCommandeMere.StatutBonCommande.BROUILLON);
                }
            }

            // Mettre à jour les champs du BC
            if (numero != null && !numero.isBlank()) {
                bonCommandeMere.setNumero(numero.trim());
            } else if (bonCommandeMere.getNumero() == null || bonCommandeMere.getNumero().isBlank()) {
                bonCommandeMere.setNumero(idGenerator.generateNumeroBC());
            }
            if (dateLivraisonPrevue != null && !dateLivraisonPrevue.isEmpty()) {
                bonCommandeMere.setDateLivraisonPrevue(LocalDate.parse(dateLivraisonPrevue).atStartOfDay());
            }
            if (fournisseurId != null && !fournisseurId.isEmpty()) {
                Fournisseur fournisseur = fournisseurService.getById(Integer.parseInt(fournisseurId));
                bonCommandeMere.setFournisseur(fournisseur);

                boolean nouveauxChampsRenseignes = nouveauResponsableNom != null && !nouveauResponsableNom.isBlank()
                        && nouveauResponsablePrenom != null && !nouveauResponsablePrenom.isBlank();

                if ("update".equals(actionResponsable)
                        && responsableFournisseurId != null && !responsableFournisseurId.isEmpty()
                        && nouveauxChampsRenseignes) {
                    // Cas : on corrige les infos du responsable déjà affecté (même personne)
                    ResponsableFournisseur rfExistant = responsableFournisseurService
                            .getById(Integer.parseInt(responsableFournisseurId))
                            .orElseThrow(() -> new IllegalArgumentException("Affectation responsable introuvable"));

                    Responsable responsable = rfExistant.getResponsable();
                    responsable.setNom(nouveauResponsableNom.trim());
                    responsable.setPrenom(nouveauResponsablePrenom.trim());
                    if (nouveauResponsableContact != null && !nouveauResponsableContact.isBlank()) {
                        responsable.setContact(nouveauResponsableContact.trim());
                    }
                    responsableService.save(responsable);
                    bonCommandeMere.setResponsableFournisseur(rfExistant);

                } else if ("new".equals(actionResponsable) && nouveauxChampsRenseignes) {
                    // Cas : on affecte une toute nouvelle personne (nouvel historique d'affectation)
                    Responsable responsable = new Responsable();
                    responsable.setNom(nouveauResponsableNom.trim());
                    responsable.setPrenom(nouveauResponsablePrenom.trim());
                    responsable.setContact(nouveauResponsableContact != null ? nouveauResponsableContact.trim() : null);
                    Responsable responsableSauvegarde = responsableService.save(responsable);

                    ResponsableFournisseur rf = new ResponsableFournisseur();
                    rf.setFournisseur(fournisseur);
                    rf.setResponsable(responsableSauvegarde);
                    rf.setDateAffectation(LocalDate.now());
                    ResponsableFournisseur rfSauvegarde = responsableFournisseurService.save(rf);

                    bonCommandeMere.setResponsableFournisseur(rfSauvegarde);

                } else if (responsableFournisseurId != null && !responsableFournisseurId.isEmpty()) {
                    // Cas normal : responsable existant sélectionné, aucune modification
                    responsableFournisseurService.getById(Integer.parseInt(responsableFournisseurId))
                            .ifPresent(bonCommandeMere::setResponsableFournisseur);

                } else if (nouveauxChampsRenseignes) {
                    // Cas initial : aucun responsable affecté -> création (comportement existant)
                    Responsable responsable = new Responsable();
                    responsable.setNom(nouveauResponsableNom.trim());
                    responsable.setPrenom(nouveauResponsablePrenom.trim());
                    responsable.setContact(nouveauResponsableContact != null ? nouveauResponsableContact.trim() : null);
                    Responsable responsableSauvegarde = responsableService.save(responsable);

                    ResponsableFournisseur rf = new ResponsableFournisseur();
                    rf.setFournisseur(fournisseur);
                    rf.setResponsable(responsableSauvegarde);
                    rf.setDateAffectation(LocalDate.now());
                    ResponsableFournisseur rfSauvegarde = responsableFournisseurService.save(rf);

                    bonCommandeMere.setResponsableFournisseur(rfSauvegarde);
                }

                boolean nouvelleAdresseRenseignee = nouvelleAdresseLibelle != null && !nouvelleAdresseLibelle.isBlank();

                if ("update".equals(actionAdresse)
                        && adresseFournisseurId != null && !adresseFournisseurId.isEmpty()
                        && nouvelleAdresseRenseignee) {
                    // Cas : on corrige l'adresse déjà affectée
                    AdresseFournisseur afExistante = adresseFournisseurService
                            .getById(Integer.parseInt(adresseFournisseurId))
                            .orElseThrow(() -> new IllegalArgumentException("Affectation adresse introuvable"));

                    Adresse adresse = afExistante.getAdresse();
                    adresse.setLibelle(nouvelleAdresseLibelle.trim());
                    adresseService.save(adresse);
                    bonCommandeMere.setAdresseFournisseur(afExistante);

                } else if ("new".equals(actionAdresse) && nouvelleAdresseRenseignee) {
                    // Cas : on affecte une toute nouvelle adresse (nouvel historique)
                    Adresse adresse = new Adresse();
                    adresse.setLibelle(nouvelleAdresseLibelle.trim());
                    Adresse adresseSauvegardee = adresseService.save(adresse);

                    AdresseFournisseur af = new AdresseFournisseur();
                    af.setFournisseur(fournisseur);
                    af.setAdresse(adresseSauvegardee);
                    af.setDateAffectation(LocalDate.now());
                    AdresseFournisseur afSauvegardee = adresseFournisseurService.save(af);

                    bonCommandeMere.setAdresseFournisseur(afSauvegardee);

                } else if (adresseFournisseurId != null && !adresseFournisseurId.isEmpty()) {
                    // Cas normal : adresse existante sélectionnée, aucune modification
                    adresseFournisseurService.getById(Integer.parseInt(adresseFournisseurId))
                            .ifPresent(bonCommandeMere::setAdresseFournisseur);

                } else if (nouvelleAdresseRenseignee) {
                    // Cas initial : aucune adresse affectée -> création
                    Adresse adresse = new Adresse();
                    adresse.setLibelle(nouvelleAdresseLibelle.trim());
                    Adresse adresseSauvegardee = adresseService.save(adresse);

                    AdresseFournisseur af = new AdresseFournisseur();
                    af.setFournisseur(fournisseur);
                    af.setAdresse(adresseSauvegardee);
                    af.setDateAffectation(LocalDate.now());
                    AdresseFournisseur afSauvegardee = adresseFournisseurService.save(af);

                    bonCommandeMere.setAdresseFournisseur(afSauvegardee);
                }
            }
            if (referenceFournisseur != null && !referenceFournisseur.isEmpty()) {
                bonCommandeMere.setReferenceFournisseur(referenceFournisseur);
            }
            if (lieuLivraison != null && !lieuLivraison.isEmpty()) {
                bonCommandeMere.setLieuLivraison(lieuLivraison);
            }
            if (montantHT != null) {
                bonCommandeMere.setMontantHT(montantHT);
            }
            if (montantTTC != null) {
                bonCommandeMere.setMontantTTC(montantTTC);
            }
            if (tauxTVA != null) {
                bonCommandeMere.setTauxTVA(tauxTVA);
            }
            if (remise != null) {
                bonCommandeMere.setRemise(remise);
            }

            bonCommandeMere.setDemandeMere(demandeMereService.getDemandeById(demandeMereId));
            if (bonCommandeMere.getStatut() == null) {
                bonCommandeMere.setStatut(BonCommandeMere.StatutBonCommande.BROUILLON);
            }
            if (bonCommandeMere.getContacts() == null || bonCommandeMere.getContacts().isEmpty()) {
                bonCommandeMere.setContacts(bcContactService.getAllContacts());
            }

            // Sauvegarder le BC mère
            BonCommandeMere bcSauvegardee = bonCommandeService.saveBonCommandeMere(bonCommandeMere);
            bonCommandeService.deleteBonCommandeFillesByBonCommandeMere(bcSauvegardee);

            // Récupérer les lignes du formulaire et créer les BonCommandeFille
            String[] demandeFilleIds = request.getParameterValues("lignes[0].demandeFilleId");
            if (demandeFilleIds != null && demandeFilleIds.length > 0) {
                int i = 0;
                while (request.getParameter("lignes[" + i + "].demandeFilleId") != null) {
                    String[] params = request.getParameterValues("lignes[" + i + "].demandeFilleId");
                    if (params != null && params.length > 0) {
                        BonCommandeFille bcFille = new BonCommandeFille();
                        bcFille.setBonCommandeMere(bcSauvegardee);

                        // Récupérer la demande fille
                        int demandeFilleId = Integer.parseInt(request.getParameter("lignes[" + i + "].demandeFilleId"));
                        DemandeFille demandeFille = demandeFilleService.getDemandeFilleById(demandeFilleId);
                        bcFille.setDemandeFille(demandeFille);

                        // Récupérer l'article
                        String articleIdParam = request.getParameter("lignes[" + i + "].articleId");
                        if (articleIdParam != null && !articleIdParam.isEmpty()) {
                            // À récupérer depuis un service d'article si disponible
                            bcFille.setArticle(demandeFille.getArticle());
                        }

                        // Définir les données de la ligne
                        String designationFournisseur = request.getParameter("lignes[" + i + "].designationFournisseur");
                        if (designationFournisseur != null) {
                            bcFille.setDesignationFournisseur(designationFournisseur);
                        }

                        String quantiteParam = request.getParameter("lignes[" + i + "].quantiteCommandee");
                        if (quantiteParam != null && !quantiteParam.isEmpty()) {
                            double quantite = Double.parseDouble(quantiteParam);
                            bcFille.setQuantiteCommandee(quantite);
                            bcFille.setQuantiteRestante(quantite);
                        }

                        String prixParam = request.getParameter("lignes[" + i + "].prixUnitaireHT");
                        if (prixParam != null && !prixParam.isEmpty()) {
                            double prix = Double.parseDouble(prixParam);
                            bcFille.setPrixUnitaireHT(prix);
                        }

                        String montantParam = request.getParameter("lignes[" + i + "].montantHT");
                        if (montantParam != null && !montantParam.isEmpty()) {
                            double montant = Double.parseDouble(montantParam);
                            bcFille.setMontantHT(montant);
                        }

                        // Sauvegarder la ligne
                        bonCommandeService.saveBonCommandeFille(bcFille);
                    }
                    i++;
                }
            }

            redirectAttributes.addFlashAttribute("ok",
                    "Bon de commande n° " + (bcSauvegardee.getNumero() != null ? bcSauvegardee.getNumero() : bcSauvegardee.getId()) + " sauvegardé avec succès avec " +
                    (demandeFilleIds != null ? demandeFilleIds.length : 0) + " ligne(s).");
            return "redirect:/bon-commande/" + bcSauvegardee.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la sauvegarde du bon de commande : " + e.getMessage());
            return "redirect:/bon-commande/list";
        }
    }

    @GetMapping("/{id}")
    public String afficherBonCommande(@PathVariable String id, Model model) {
        BonCommandeMere bonCommandeMere = bonCommandeService.getBonCommandeMereById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bon de commande non trouvé"));
        List<BonCommandeFille> lignes = bonCommandeService.getBonCommandeFillesByBonCommandeMere(bonCommandeMere);

        if (bonCommandeMere.getStatut() == BonCommandeMere.StatutBonCommande.BROUILLON) {
            model.addAttribute("demandeMereId", bonCommandeMere.getDemandeMere().getId());
            model.addAttribute("bonCommandeMere", bonCommandeMere);
            model.addAttribute("bcContacts", bcContactService.getAllContacts());
            model.addAttribute("bonCommandeFilles", lignes);

            // --- Fournisseurs enrichis avec leur responsable actuel ---
            List<FournisseurAutocompleteDTO> fournisseursDTO = Arrays.stream(fournisseurService.getAllFournisseurs())
                    .map(f -> {
                        var respOpt = responsableFournisseurService.getResponsableActuel(f.getId());
                        Integer rfId = respOpt.map(ResponsableFournisseur::getId).orElse(null);
                        Integer respId = respOpt.map(rf -> rf.getResponsable().getId()).orElse(null);
                        String respNom = respOpt
                                .map(rf -> rf.getResponsable().getPrenom() + " " + rf.getResponsable().getNom())
                                .orElse("");

                        var adrOpt = adresseFournisseurService.getAdresseActuel(f.getId());
                        Integer afId = adrOpt.map(AdresseFournisseur::getId).orElse(null);
                        Integer adrId = adrOpt.map(af -> af.getAdresse().getId()).orElse(null);
                        String adrLibelle = adrOpt.map(af -> af.getAdresse().getLibelle()).orElse("");

                        return new FournisseurAutocompleteDTO(
                                f.getId(), f.getNom(),
                                rfId, respId, respNom,
                                afId, adrId, adrLibelle
                        );
                    })
                    .toList();
            model.addAttribute("fournisseurs", fournisseursDTO);

            if (bonCommandeMere.getFournisseur() != null) {
                responsableFournisseurService
                        .getResponsableActuel(bonCommandeMere.getFournisseur().getId())
                        .ifPresent(respActuel -> model.addAttribute("responsableFournisseurActuel", respActuel));

                adresseFournisseurService
                        .getAdresseActuel(bonCommandeMere.getFournisseur().getId())
                        .ifPresent(adrActuel -> model.addAttribute("adresseFournisseurActuel", adrActuel));
            }

            // --- Pré-remplissage du responsable si un fournisseur est déjà sélectionné ---
            if (bonCommandeMere.getFournisseur() != null) {
                responsableFournisseurService
                        .getResponsableActuel(bonCommandeMere.getFournisseur().getId())
                        .ifPresent(respActuel -> model.addAttribute("responsableFournisseurActuel", respActuel));
            }

            return "bc/bon-commande-saisie";
        }

        model.addAttribute("bonCommandeMere", bonCommandeMere);
        model.addAttribute("lignes", lignes);
        return "bc/bon-commande-fiche";
    }

    private BonCommandeMere initialiserBonCommandeDepuisDemande(DemandeMere demandeMere, Utilisateur utilisateur) {
        BonCommandeMere bonCommandeMere = new BonCommandeMere();
        bonCommandeMere.setNumero(idGenerator.generateNumeroBC());
        bonCommandeMere.setDateCreation(LocalDateTime.now());
        bonCommandeMere.setLieuLivraison("");
        bonCommandeMere.setReferenceFournisseur("");
        bonCommandeMere.setStatut(BonCommandeMere.StatutBonCommande.BROUILLON);
        bonCommandeMere.setDescription(demandeMere.getMotifEvoque());
        bonCommandeMere.setCreateur(utilisateur);
        bonCommandeMere.setDemandeMere(demandeMere);
        bonCommandeMere.setContacts(bcContactService.getAllContacts());
        return bonCommandeMere;
    }

    private List<BonCommandeFille> initialiserLignesBonCommande(DemandeMere demandeMere, BonCommandeMere bonCommandeMere) {
        List<DemandeFille> demandeFilles = demandeFilleService.getDemandeFilleByDemandeMere(demandeMere);
        List<BonCommandeFille> bonCommandeFilles = new ArrayList<>();

        for (DemandeFille demandeFille : demandeFilles) {
            BonCommandeFille bcFille = new BonCommandeFille();
            bcFille.setBonCommandeMere(bonCommandeMere);
            bcFille.setDemandeFille(demandeFille);
            bcFille.setArticle(demandeFille.getArticle());
            bcFille.setQuantiteCommandee(demandeFille.getQuantite());
            bcFille.setQuantiteRestante(demandeFille.getQuantite());

            if (demandeFille.getPrixUnitaire() != null) {
                bcFille.setPrixUnitaireHT(demandeFille.getPrixUnitaire());
                bcFille.setMontantHT(demandeFille.getQuantite() * demandeFille.getPrixUnitaire());
            }
            if (demandeFille.getMontantEstime() != null) {
                bcFille.setMontantTTC(demandeFille.getMontantEstime());
            }

            bonCommandeFilles.add(bcFille);
        }
        return bonCommandeFilles;
    }
}
