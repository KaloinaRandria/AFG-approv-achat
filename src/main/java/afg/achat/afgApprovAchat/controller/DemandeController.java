package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.email.EmailSenderService;
import afg.achat.afgApprovAchat.email.Mail;
import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.DTO.LigneRestanteDTO;
import afg.achat.afgApprovAchat.DTO.ScinderDTO;
import afg.achat.afgApprovAchat.model.demande.*;
import afg.achat.afgApprovAchat.model.util.*;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereSpec;
import afg.achat.afgApprovAchat.repository.stock.StockFilleRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.BonSortieService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.demande.*;
import afg.achat.afgApprovAchat.service.stock.LotStockService;
import afg.achat.afgApprovAchat.service.util.CommentaireFinanceService;
import afg.achat.afgApprovAchat.service.util.HistoriqueFinanceDemandeService;
import afg.achat.afgApprovAchat.service.util.ModeTraitementService;
import afg.achat.afgApprovAchat.service.util.PrixArticleService;
import afg.achat.afgApprovAchat.service.workflow.DecisionDemande;
import afg.achat.afgApprovAchat.service.workflow.DroitsDecision;
import afg.achat.afgApprovAchat.service.workflow.PieceJointeWorkflowService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import afg.achat.afgApprovAchat.service.workflow.WorkflowService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.upload.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/demande")
@RequiredArgsConstructor
public class DemandeController {

    private final DemandeMereService demandeMereService;

    private final DemandeFilleService demandeFilleService;

    private final ArticleService articleService;

    private final UtilisateurService utilisateurService;

    private final IdGenerator idGenerator;

    private final CentreBudgetaireService centreBudgetaireService;

    private final StorageService storageService;

    private final DemandePieceJointeService demandePieceJointeService;

    private final ValidationDemandeService validationDemandeService;

    private final CodepPieceJointeService codepPieceJointeService;

    private final  CommentaireFinanceService commentaireFinanceService;
    private final HistoriqueFinanceDemandeService historiqueFinanceDemandeService;

    private final EmailSenderService ess;

    private final PrixArticleService prixArticleService;

    private final BonSortieService bonSortieService;

    private final BonCommandeService bonCommandeService;

    private final StockFilleRepo stockFilleRepo;

    private final LotStockService lotStockService;

    private final ModeTraitementService modeTraitementService;

    private final WorkflowService workflowService;

    private final PieceJointeWorkflowService pieceJointeService;

    private final afg.achat.afgApprovAchat.service.stock.ReservationStockService reservationStockService;

    private final afg.achat.afgApprovAchat.service.demande.ApprovisionnementLigneService approvisionnementLigneService;

    private final afg.achat.afgApprovAchat.service.util.DeviseService deviseService;

    private final afg.achat.afgApprovAchat.service.demande.DeviseDemandeService deviseDemandeService;

    private final DemandeAutreService demandeAutreService;

    private final DemandeBrouillonService demandeBrouillonService;

    /**
     * Les Moyens Généraux, à leur étape, choisissent la devise des prix proforma.
     * Les prix des lignes à acheter sont à ressaisir ; les lignes stock sont converties.
     */
    @PostMapping("/fiche/{id}/devise")
    public String changerDevise(@PathVariable("id") String id,
                                @RequestParam(name = "devise", required = false) String deviseId,
                                RedirectAttributes redirectAttributes) {
        String fiche = "redirect:/demande/fiche/" + id;
        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!workflowService.calculerDroits(demande, utilisateurCourant(auth), auth).mg()) {
            redirectAttributes.addFlashAttribute("ko", "Seuls les Moyens Généraux, à leur étape, choisissent la devise.");
            return fiche;
        }
        // Demande Autre : la devise est celle du montant saisi par le demandeur
        if (demande.getTypeDemandeEffectif().isDepenseAutre()) {
            redirectAttributes.addFlashAttribute("ko", "La devise d'une demande Autre est fixée à la saisie.");
            return fiche;
        }
        try {
            int aRessaisir = deviseDemandeService.changerDevise(demande, deviseService.lireDevise(deviseId));
            redirectAttributes.addFlashAttribute("ok", "Devise de la demande : " + demande.getCodeDevise()
                    + (aRessaisir > 0 ? ". Ressaisissez le prix proforma de " + aRessaisir + " article(s) dans cette devise." : "."));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return fiche;
    }

    @GetMapping("/add")
    public String addDemandePage(@RequestParam(name = "type", required = false) String type,
                                 Model model, HttpServletRequest request, HttpSession session) {

        // Matériel par défaut ; « type=AUTRE » ouvre le formulaire des demandes Autres
        TypeDemande typeDemande;
        try {
            typeDemande = TypeDemande.parse(type);
        } catch (IllegalArgumentException e) {
            typeDemande = null;
        }
        if (typeDemande == null || !typeDemande.isCreable()) {
            typeDemande = TypeDemande.FOURNITURE;
        }
        return formulaireSaisie(typeDemande, null, model, session);
    }

    /** Le créateur reprend son brouillon dans le formulaire de saisie. */
    @GetMapping("/{id}/modifier")
    public String modifierBrouillon(@PathVariable("id") String id, Model model, HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable : " + id);
            return "redirect:/demande/list";
        }
        Utilisateur current = utilisateurCourant(SecurityContextHolder.getContext().getAuthentication());
        if (!DemandeBrouillonService.peutModifier(demande, current)) {
            redirectAttributes.addFlashAttribute("ko", "Seul le créateur d'une demande en brouillon peut la modifier.");
            return "redirect:/demande/fiche/" + id;
        }
        return formulaireSaisie(demande.getTypeDemandeEffectif(), demande, model, session);
    }

    /** Formulaire de saisie d'une nouvelle demande, ou d'un brouillon à modifier ({@code demande} non nul). */
    private String formulaireSaisie(TypeDemande typeDemande, DemandeMere demande, Model model, HttpSession session) {
        model.addAttribute("typeDemande", typeDemande);
        model.addAttribute("demande", demande);

        // Token anti double-soumission
        String token = UUID.randomUUID().toString();
        session.setAttribute("submissionToken", token);
        model.addAttribute("submissionToken", token);

        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
        model.addAttribute("ligneBudgetaires", centreBudgetaireService.getAllCentreBudgetaires());
        // Récapitulatif de soumission : demandeur, N+1 qui recevra la demande, circuit de validation
        Utilisateur demandeurCourant = utilisateurCourant(SecurityContextHolder.getContext().getAuthentication());
        model.addAttribute("demandeurCourant", demandeurCourant);
        model.addAttribute("superieurDemandeur", demandeurCourant.getSuperieurHierarchique());
        model.addAttribute("circuitValidation", EtapeWorkflow.libellesCircuit(false, typeDemande));
        model.addAttribute("piecesJointesExistantes", demande == null
                ? List.of() : demandePieceJointeService.listByDemandeId(demande.getId()));

        if (typeDemande.isDepenseAutre()) {
            model.addAttribute("categories", demandeAutreService.getCategoriesActives());
            model.addAttribute("devises", deviseService.getAllDevises());
            model.addAttribute("deviseReference", deviseService.getDeviseReference());
            model.addAttribute("detailAutre", demande == null ? null : demandeAutreService.getDetail(demande).orElse(null));
            return "demande/demande-autre-saisie";
        }
        List<DemandeFille> lignes = demande == null ? List.of() : demandeFilleService.getDemandeFilleByDemandeMere(demande);
        model.addAttribute("lignesBrouillon", lignes);
        return "demande/demande-saisie";
    }

    /** Vrai si le formulaire demande la soumission (sinon : enregistrement en brouillon). */
    private static boolean demandeSoumission(String action) {
        return "soumettre".equalsIgnoreCase(action);
    }

    /** Jeton anti double-soumission : consommé au premier envoi. */
    private static boolean consommerJeton(HttpSession session, String submissionToken) {
        String sessionToken = (String) session.getAttribute("submissionToken");
        if (sessionToken == null || !sessionToken.equals(submissionToken)) return false;
        session.removeAttribute("submissionToken");
        return true;
    }

    /**
     * Demande de matériel : création ou modification du brouillon, puis soumission si demandée
     * (bouton « Soumettre »). La demande reste modifiable par son créateur tant qu'elle n'est pas soumise.
     */
    @PostMapping("/save")
    public String insertDemande(@RequestParam(name = "id", required = false) String id,
                                @RequestParam(name = "action", required = false) String action,
                                @RequestParam(name = "dateSortie", required = false) String dateSortie,
                                @RequestParam(name = "motif", required = false) String motif,
                                @RequestParam(name = "description", required = false) String description,
                                @RequestParam(name = "articleCodes[]", required = false) List<String> articleCodes,
                                @RequestParam(name = "quantite[]", required = false) List<String> quantite,
                                @RequestParam(name = "priorite", required = false) String priorite,
                                @RequestParam(name = "typeDemande", required = false) String typeDemande,
                                @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
                                @RequestParam(name = "pjASupprimer", required = false) List<Integer> pjASupprimer,
                                @RequestParam(name = "submissionToken") String submissionToken,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!consommerJeton(session, submissionToken)) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    "Cette demande a déjà été envoyée. Veuillez vérifier la liste des demandes.");
            return "redirect:/demande/list";
        }

        TypeDemande type;
        try {
            type = TypeDemande.parse(typeDemande);
        } catch (IllegalArgumentException e) {
            type = null;
        }
        // Ce formulaire crée les demandes de matériel ; les demandes Autres ont le leur
        if (type == TypeDemande.AUTRE) {
            return "redirect:/demande/add?type=" + TypeDemande.AUTRE.name();
        }
        // Les anciennes demandes hors matériel ne se créent plus
        if (type == null || !type.isCreable()) {
            redirectAttributes.addFlashAttribute("ko", "Seule une demande de matériel peut être créée.");
            return "redirect:/demande/add";
        }
        final String retourSaisie = (id == null || id.isBlank())
                ? "redirect:/demande/add"
                : "redirect:/demande/" + id + "/modifier";

        Utilisateur utilisateur = utilisateurCourant(SecurityContextHolder.getContext().getAuthentication());
        DemandeMere demande;
        try {
            demande = demandeBrouillonService.enregistrerMateriel(id, utilisateur,
                    new DemandeBrouillonService.SaisieMateriel(dateSortie, priorite, motif, description,
                            articleCodes, quantite),
                    piecesJointes, pjASupprimer);
        } catch (IllegalArgumentException | WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return retourSaisie;
        } catch (Exception e) {
            log.error("Échec de l'enregistrement d'une demande de matériel", e);
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'enregistrement : " + e.getMessage());
            return retourSaisie;
        }
        return apresEnregistrement(demande, utilisateur, action, redirectAttributes);
    }

    /**
     * Demande Autre : une seule dépense hors matériel (sponsoring, remboursement, indemnité de repas…),
     * réglée en paiement direct. Circuit complet jusqu'au S.G. Brouillon puis soumission, comme le matériel.
     */
    @PostMapping("/autre/save")
    public String insertDemandeAutre(@RequestParam(name = "id", required = false) String id,
                                     @RequestParam(name = "action", required = false) String action,
                                     @RequestParam(name = "categorie", required = false) String categorie,
                                     @RequestParam(name = "beneficiaire", required = false) String beneficiaire,
                                     @RequestParam(name = "quantite", required = false) String quantite,
                                     @RequestParam(name = "montantUnitaire", required = false) String montantUnitaire,
                                     @RequestParam(name = "devise", required = false) String devise,
                                     @RequestParam(name = "dateSortie", required = false) String datePaiement,
                                     @RequestParam(name = "priorite", required = false) String priorite,
                                     @RequestParam(name = "motif", required = false) String motif,
                                     @RequestParam(name = "description", required = false) String description,
                                     @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
                                     @RequestParam(name = "pjASupprimer", required = false) List<Integer> pjASupprimer,
                                     @RequestParam(name = "submissionToken") String submissionToken,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        if (!consommerJeton(session, submissionToken)) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    "Cette demande a déjà été envoyée. Veuillez vérifier la liste des demandes.");
            return "redirect:/demande/list?typeDemande=" + TypeDemande.AUTRE.name();
        }

        final String retourSaisie = (id == null || id.isBlank())
                ? "redirect:/demande/add?type=" + TypeDemande.AUTRE.name()
                : "redirect:/demande/" + id + "/modifier";
        Utilisateur utilisateur = utilisateurCourant(SecurityContextHolder.getContext().getAuthentication());

        DemandeMere demande;
        try {
            demande = demandeAutreService.enregistrer(id, utilisateur, new DemandeAutreService.NouvelleDemandeAutre(
                    categorie, beneficiaire, quantite, montantUnitaire, devise,
                    datePaiement, priorite, motif, description), piecesJointes, pjASupprimer);
        } catch (IllegalArgumentException | WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return retourSaisie;
        } catch (Exception e) {
            log.error("Échec de l'enregistrement d'une demande Autre", e);
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'enregistrement : " + e.getMessage());
            return retourSaisie;
        }
        return apresEnregistrement(demande, utilisateur, action, redirectAttributes);
    }

    /**
     * Après l'enregistrement du brouillon : soumission si demandée, sinon retour sur la fiche du brouillon.
     * Le brouillon reste enregistré si la soumission est refusée.
     */
    private String apresEnregistrement(DemandeMere demande, Utilisateur utilisateur, String action,
                                       RedirectAttributes redirectAttributes) {
        if (!demandeSoumission(action)) {
            redirectAttributes.addFlashAttribute("ok", "Demande n° " + demande.getId()
                    + " enregistrée en brouillon. Vous pouvez encore la modifier ; soumettez-la pour lancer la validation.");
            return "redirect:/demande/fiche/" + demande.getId();
        }
        return soumettreEtNotifier(demande.getId(), utilisateur, redirectAttributes);
    }

    /** Le créateur soumet son brouillon depuis la fiche. */
    @PostMapping("/fiche/{id}/soumettre")
    public String soumettreBrouillon(@PathVariable("id") String id, RedirectAttributes redirectAttributes) {
        Utilisateur utilisateur = utilisateurCourant(SecurityContextHolder.getContext().getAuthentication());
        return soumettreEtNotifier(id, utilisateur, redirectAttributes);
    }

    private String soumettreEtNotifier(String id, Utilisateur utilisateur, RedirectAttributes redirectAttributes) {
        DemandeMere demande;
        try {
            demande = demandeBrouillonService.soumettre(id, utilisateur);
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/demande/fiche/" + id;
        }
        // Après le commit : un échec d'envoi ne remet pas la soumission en cause
        try {
            notifierCreation(demande, utilisateur);
        } catch (Exception e) {
            log.error("Échec des notifications de soumission de la demande {}", demande.getId(), e);
        }
        redirectAttributes.addFlashAttribute("ok", "Demande n° " + demande.getId()
                + " soumise : elle est en attente de validation par votre N+1.");
        return "redirect:/demande/list?typeDemande=" + demande.getTypeDemandeEffectif().name();
    }

    /** Le créateur abandonne son brouillon. */
    @PostMapping("/fiche/{id}/supprimer-brouillon")
    public String supprimerBrouillon(@PathVariable("id") String id, RedirectAttributes redirectAttributes) {
        Utilisateur utilisateur = utilisateurCourant(SecurityContextHolder.getContext().getAuthentication());
        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        String liste = demande == null
                ? "redirect:/demande/list"
                : "redirect:/demande/list?typeDemande=" + demande.getTypeDemandeEffectif().name();
        try {
            demandeBrouillonService.supprimer(id, utilisateur);
            redirectAttributes.addFlashAttribute("ok", "Brouillon n° " + id + " supprimé.");
            return liste;
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/demande/fiche/" + id;
        }
    }

    /** Mails de création : confirmation au demandeur, puis action requise pour le N+1 et les validateurs assignés. */
    private void notifierCreation(DemandeMere demandeMere, Utilisateur utilisateur) {
            // ── Mail 1 : confirmation au demandeur ──────────────────────────────────
            Map<String, Object> propsDemandeur = new HashMap<>();
            propsDemandeur.put("id",          demandeMere.getId());
            propsDemandeur.put("demandeur",   demandeMere.getDemandeur());
            propsDemandeur.put("dateDemande", LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            propsDemandeur.put("Validateur", demandeMere.getDemandeur().getSuperieurHierarchique());

            Mail mail = new Mail(
                    "demandeSaved",
                    demandeMere.getDemandeur().getMail(),
                    "[AFG Bank - Demande Achat] - Demande N°" + demandeMere.getId() + " en cours de validation",
                    propsDemandeur
            );
            ess.sendEmail(mail);

// ── Mail 2 : notification au N+1 pour validation ────────────────────────
            Utilisateur superieur = demandeMere.getDemandeur().getSuperieurHierarchique();

            if (superieur != null && superieur.getMail() != null) {
                Map<String, Object> propsSup = new HashMap<>();
                propsSup.put("id",           demandeMere.getId());
                propsSup.put("demandeur",    demandeMere.getDemandeur());
                propsSup.put("destinataire", superieur);
                propsSup.put("validateur",   demandeMere.getDemandeur());
                propsSup.put("etape",        StatutDemande.getLibelle(StatutDemande.CREE));
                propsSup.put("dateDemande", LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                propsSup.put("lienValidation", ess.lienFiche(demandeMere.getId()));

                Mail mailSup = new Mail(
                        "validSup",
                        superieur.getMail(),
                        "[AFG Bank - Demande Achat] - Action requise : Validation de la demande N°" + demandeMere.getId(),
                        propsSup
                );
                ess.sendEmail(mailSup);
            }

            // ── Mail 3 : notification aux validateurs ────────────────────────────────
            Set<Utilisateur> validateurs = utilisateur.getValidateurs();

            if (validateurs != null && !validateurs.isEmpty()) {
                for (Utilisateur validateur : validateurs) {
                    if (validateur == null || validateur.getMail() == null) continue;

                    Map<String, Object> propsValidateur = new HashMap<>();
                    propsValidateur.put("id",           demandeMere.getId());
                    propsValidateur.put("demandeur",    demandeMere.getDemandeur());
                    propsValidateur.put("destinataire", validateur);
                    propsValidateur.put("validateur",   demandeMere.getDemandeur());
                    propsValidateur.put("etape",        StatutDemande.getLibelle(StatutDemande.CREE));
                    propsValidateur.put("dateDemande",  LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                    propsValidateur.put("lienValidation", ess.lienFiche(demandeMere.getId()));

                    Mail mailValidateur = new Mail(
                            "validSup",
                            validateur.getMail(),
                            "[AFG Bank - Demande Achat] - Action requise : Validation de la demande N°" + demandeMere.getId(),
                            propsValidateur
                    );
                    ess.sendEmail(mailValidateur);
                }
            }
    }


    @GetMapping("/list")
    public String listDemandePage(
            Model model,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0")           int page,
            @RequestParam(defaultValue = "10")          int size,
            @RequestParam(defaultValue = "dateDemande") String sort,
            @RequestParam(defaultValue = "desc")        String dir,
            @RequestParam(required = false)             Integer  statut,
            @RequestParam(required = false)             String   priorite,
            @RequestParam(required = false)             String   motif,
            @RequestParam(required = false)             String   num,
            @RequestParam(required = false)             String   demandeur,
            @RequestParam(required = false)             String   type,
            @RequestParam(required = false)             String   typeDemande,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "ALL") String scope) {

        // ── 1. Utilisateur courant ───────────────────────────────────────────────
        Authentication auth    = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal  = (Utilisateur) auth.getPrincipal();
        Utilisateur current    = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin      = hasRole(auth, "ROLE_ADMIN");
        boolean isMG         = hasRole(auth, "ROLE_MOYENS_GENERAUX");
        boolean isControleur = hasRole(auth, "ROLE_CONTROLEUR");
        boolean isDFC        = hasRole(auth, "ROLE_DFC");
        boolean isSG         = hasRole(auth, "ROLE_SG");
        boolean isAudit      = hasRole(auth, "ROLE_AUDIT");
        boolean isBackoffice = isAdmin || isAudit || isMG || isControleur || isDFC || isSG;

        // ── 2. IDs visibles : hiérarchie + validateurs assignés ─────────────────
        List<Integer> visibleIds  = utilisateurService.getIdsUtilisateurVisible(current.getId());
        List<Integer> idsAValider = utilisateurService.getIdsUtilisateursAValider(current.getId());

        Set<Integer>  allVisibleSet  = new HashSet<>(visibleIds);
        allVisibleSet.addAll(idsAValider);
        List<Integer> allVisibleIds  = new ArrayList<>(allVisibleSet);
        boolean       hasChildren    = allVisibleIds.size() > 1;

        // ── 3. Filtre statut normalisé ───────────────────────────────────────────
        Integer statutFilter = (statut == null || statut == 0) ? null : statut;

        // ── 4. Construction du SearchCriteria selon le rôle ─────────────────────
        DemandeMereSpec.SearchCriteria.Builder criteriaBuilder = DemandeMereSpec.SearchCriteria.builder()
                .num(num)
                .demandeur(demandeur)
                .type(type)
                .typeDemande(typeDemande)
                .priorite(priorite)
                .motif(motif)
                .statut(statutFilter)
                .brouillonsDe(current.getId())
                .dateFrom(DemandeMereService.toFrom(dateFrom))
                .dateTo(DemandeMereService.toTo(dateTo));

        if (isAdmin || isAudit) {
            // Admin : aucune restriction sur les demandeurs ni sur les statuts

        } else if (isMG || isControleur || isDFC || isSG) {
            // Backoffice : voit les demandes dans ses statuts autorisés
            //              OU ses propres demandes (tous statuts)
            List<Integer> roleStatuts = resolveStatutsAutorises(
                    isMG, isControleur, isDFC, isSG, statutFilter);
            criteriaBuilder
                    .roleStatuts(roleStatuts)
                    .myStatut(statutFilter)
                    .myVisibleIds(allVisibleIds.isEmpty() ? List.of(-1) : allVisibleIds);

        } else {
            // Utilisateur simple : uniquement ses demandes + celles qu'il doit valider
            List<Integer> idsToUse = resolveScope(scope, current, allVisibleIds);
            criteriaBuilder.demandeurIds(idsToUse.isEmpty() ? List.of(-1) : idsToUse);
        }

        // ── 5. Exécution de la recherche ─────────────────────────────────────────
        Page<DemandeMere> demandesMeres = demandeMereService.search(
                criteriaBuilder.build(), page, size, sort, dir);

        // ── 6. Alimentation du modèle Thymeleaf ──────────────────────────────────
        populateModel(
                model, demandesMeres,
                statut, priorite, motif, num, demandeur, type, typeDemande,
                dateFrom, dateTo, scope,
                size, sort, dir,
                isBackoffice, hasChildren,
                isMG, isControleur, isDFC, isSG, isAdmin
        );
        String returnUrl = buildReturnUrl(page, size, sort, dir, statut, priorite, motif, num, demandeur, type, typeDemande, dateFrom, dateTo, scope);
        model.addAttribute("returnUrl", returnUrl);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));

        model.addAttribute("motif",                  motif);
        model.addAttribute("nbUtilisateursAValider",  idsAValider.size());
        model.addAttribute("hasDemandesAValider",     !idsAValider.isEmpty());
        model.addAttribute("StatutValide",            StatutDemande.VALIDE);

        return "demande/demande-liste";
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private static boolean hasRole(Authentication auth, String role) {
        return WorkflowService.hasRole(auth, role);
    }

    private static boolean hasFinanceAccess(Authentication auth) {
        return WorkflowService.hasFinanceAccess(auth);
    }

    private static boolean hasFinanceReadAccess(Authentication auth) {
        return WorkflowService.hasFinanceReadAccess(auth);
    }

    private List<Integer> resolveScope(String scope, Utilisateur current,
                                       List<Integer> allVisibleIds) {
        return switch (scope == null ? "ALL" : scope.toUpperCase()) {
            case "ME"       -> List.of(current.getId());
            case "CHILDREN" -> allVisibleIds.stream()
                    .filter(id -> !id.equals(current.getId()))
                    .toList();
            default         -> allVisibleIds;
        };
    }

    private List<Integer> resolveStatutsAutorises(boolean isMG, boolean isControleur,
                                                  boolean isDFC, boolean isSG,
                                                  Integer statutFilter) {
        List<Integer> base;
        if (isMG) {
            base = List.of(StatutDemande.VALIDATION_N1, StatutDemande.VALIDATION_N2,
                    StatutDemande.VALIDATION_N3, StatutDemande.VALIDATION_N4,
                    StatutDemande.DECISION_CODEP, StatutDemande.VALIDE, StatutDemande.REFUSE);
        } else if (isControleur) {
            base = List.of(StatutDemande.VALIDATION_N2, StatutDemande.VALIDATION_N3,
                    StatutDemande.VALIDATION_N4, StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDE, StatutDemande.REFUSE);
        } else if (isDFC) {
            base = List.of(StatutDemande.VALIDATION_N3, StatutDemande.VALIDATION_N4,
                    StatutDemande.DECISION_CODEP, StatutDemande.VALIDE, StatutDemande.REFUSE);
        } else { // isSG
            base = List.of(StatutDemande.VALIDATION_N4, StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDE, StatutDemande.REFUSE);
        }

        if (statutFilter != null) {
            return base.contains(statutFilter) ? List.of(statutFilter) : List.of();
        }
        return new java.util.ArrayList<>(base);
    }

    private void populateModel(Model model, Page<DemandeMere> demandesMeres,
                               Integer statut, String priorite, String motif, String num,
                               String demandeur, String type, String typeDemande,
                               LocalDate dateFrom, LocalDate dateTo,
                               String scope, int size, String sort, String dir,
                               boolean isBackofficeValidator, boolean hasChildren,
                               boolean isMG, boolean isControleur,
                               boolean isDFC, boolean isSG, boolean isAdmin) {

        // Libellés, badges et icônes : source unique EtapeWorkflow
        model.addAttribute("statutLabels", EtapeWorkflow.libellesAttente());
        model.addAttribute("statutFiltre", EtapeWorkflow.libellesAttente());
        model.addAttribute("badgeClasses", EtapeWorkflow.badgesListe());
        model.addAttribute("badgeIcons",   EtapeWorkflow.icones());

        // Priorité filtre
        Map<String, String> prioriteFiltre = new LinkedHashMap<>();
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P2), "P2");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P1), "P1");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P0), "P0");
        model.addAttribute("prioriteFiltre", prioriteFiltre);

        // Visibilité colonnes
        model.addAttribute("showDemandeurColumn",      isBackofficeValidator || hasChildren);
        model.addAttribute("showDemandeurScopeFilter", hasChildren && !isBackofficeValidator);

        // Flags de vue
        model.addAttribute("isMGOnly",         isMG && !isAdmin);
        model.addAttribute("isControleurOnly", isControleur && !isAdmin);
        model.addAttribute("isDFCOnly",        isDFC && !isAdmin);
        model.addAttribute("isSGOnly",         isSG && !isAdmin);

        // Colspan calculé (évite l'expression #authorization fragile dans Thymeleaf)
        int colCount = 6; // id + date + motif + type + priorité + statut
        if (isBackofficeValidator || hasChildren) colCount++;
        model.addAttribute("tableColCount", colCount);

        // Données pagination et filtres actifs
        model.addAttribute("demandesMeres", demandesMeres);
        model.addAttribute("statut",    statut == null ? 0 : statut);
        model.addAttribute("priorite",  priorite  == null ? "" : priorite);
        model.addAttribute("motif",     motif     == null ? "" : motif);
        model.addAttribute("num",       num       == null ? "" : num);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("type",      type      == null ? "" : type);
        // Type affiché : normalisé, vide si absent ou inconnu (liste de toutes les demandes)
        TypeDemande typeListe;
        try {
            typeListe = TypeDemande.parse(typeDemande);
        } catch (IllegalArgumentException e) {
            typeListe = null;
        }
        model.addAttribute("typeDemande",  typeListe == null ? "" : typeListe.name());
        model.addAttribute("titreListe",   typeListe == null ? "Toutes les demandes" : typeListe.getLibelleListe());
        model.addAttribute("typesDemande", TypeDemande.values());
        model.addAttribute("dateFrom",  dateFrom);
        model.addAttribute("dateTo",    dateTo);
        model.addAttribute("scope",     scope);
        model.addAttribute("page",      demandesMeres.getNumber());
        model.addAttribute("size",      size);
        model.addAttribute("sort",      sort);
        model.addAttribute("dir",       dir);

        model.addAttribute("natures",   DemandeMere.NatureDemande.values());
        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
    }

    private String buildReturnUrl(int page, int size, String sort, String dir,
                                  Integer statut, String priorite, String motif,
                                  String num, String demandeur, String type, String typeDemande,
                                  LocalDate dateFrom, LocalDate dateTo, String scope) {
        StringBuilder sb = new StringBuilder("/demande/list?");
        sb.append("page=").append(page);
        sb.append("&size=").append(size);
        sb.append("&sort=").append(sort);
        sb.append("&dir=").append(dir);
        if (statut  != null)    sb.append("&statut=").append(statut);
        if (priorite != null)   sb.append("&priorite=").append(priorite);
        if (motif   != null)    sb.append("&motif=").append(motif);
        if (num     != null)    sb.append("&num=").append(num);
        if (demandeur != null)  sb.append("&demandeur=").append(demandeur);
        if (type    != null)    sb.append("&type=").append(type);
        if (typeDemande != null && !typeDemande.isBlank()) sb.append("&typeDemande=").append(typeDemande);
        if (dateFrom != null)   sb.append("&dateFrom=").append(dateFrom);
        if (dateTo  != null)    sb.append("&dateTo=").append(dateTo);
        if (scope   != null)    sb.append("&scope=").append(scope);
        return sb.toString();
    }

    @PostMapping("/fiche/{id}/type-demande")
    public String updateTypeDemande(@PathVariable("id") String id,
                                    @RequestParam(value = "typeDemande", required = false) String typeDemande,
                                    RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminOrMG = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_MOYENS_GENERAUX")
        );

        if (!isAdminOrMG) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé.");
            return "redirect:/demande/fiche/" + id;
        }

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        // typeDemande = "OPEX" / "CAPEX" / "" (null)
        if (typeDemande == null || typeDemande.isBlank()) {
            demande.setNatureDemande(null);
        } else {
            demande.setNatureDemande(DemandeMere.NatureDemande.valueOf(typeDemande.trim().toUpperCase()));
        }

        demandeMereService.saveDemandeMere(demande);
        redirectAttributes.addFlashAttribute("ok", "Type de demande enregistré.");

        return "redirect:/demande/fiche/" + id;
    }

    @PostMapping("/ligne/{id}/refuser")
    public String refuserLigne(@PathVariable int id,
                               @RequestParam(value = "commentaire", required = false) String commentaire,
                               RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur current = utilisateurCourant(auth);
        DemandeFille ligne = demandeFilleService.getDemandeFilleById(id);
        if (ligne == null) {
            redirectAttributes.addFlashAttribute("ko", "Ligne introuvable.");
            return "redirect:/demande/list";
        }
        DemandeMere demande = ligne.getDemandeMere();
        if (!workflowService.calculerDroits(demande, current, auth).auMoinsUn()
                || demande.getStatut() == StatutDemande.DECISION_CODEP) {
            redirectAttributes.addFlashAttribute("ko", "Vous ne pouvez pas refuser une ligne de cette demande.");
            return "redirect:/demande/fiche/" + demande.getId();
        }
        try {
            demandeFilleService.refuserLigne(ligne, current, commentaire);
            redirectAttributes.addFlashAttribute("ok", "Ligne refusée avec succès.");
            return "redirect:/demande/fiche/" + ligne.getDemandeMere().getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur : " + e.getMessage());
            return "redirect:/demande/fiche/" + ligne.getDemandeMere().getId();
        }
    }

    @PostMapping("/ligne/{ligneId}/quantite")
    public ResponseEntity<?> updateQuantiteLigne(@PathVariable Integer ligneId,
                                                 @RequestParam("quantite") double quantite) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur current = utilisateurCourant(auth);

        DemandeFille ligne = demandeFilleService.getDemandeFilleById(ligneId);
        if (ligne == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Ligne introuvable"));
        }

        // Modification réservée au N+1 (étape N+1) et aux MG (étape MG)
        DroitsDecision droits = workflowService.calculerDroits(ligne.getDemandeMere(), current, auth);
        if (!droits.n1() && !droits.mg()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Vous ne pouvez pas modifier la quantité à cette étape."));
        }

        if (quantite <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La quantité doit être supérieure à 0"));
        }

        // VÉRIFICATION STOCK POUR LES LIGNES STOCK
        if (ligne.getTypeApprovisionnement() == DemandeFille.TypeApprovisionnement.STOCK) {
            // Stock libre pour CETTE ligne : lots moins les réservations de toutes les autres lignes
            // STOCK (toutes demandes). La quantité déjà sortie de la ligne n'est plus dans les lots.
            double dejaSorti = bonSortieService.getQuantiteDejaSortie(ligne);
            double stockReelDisponible = reservationStockService.stockLibre(
                    ligne.getArticle().getCodeArticle(), List.of(ligneId)) + dejaSorti;

            if (quantite > stockReelDisponible + 0.001) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", String.format(
                                "Stock insuffisant pour l'article %s - %s. " +
                                        "Stock disponible : %.2f, Demandé : %.2f",
                                ligne.getArticle().getCodeArticle(),
                                ligne.getArticle().getDesignation(),
                                stockReelDisponible,
                                quantite
                        )));
            }
        }

        // Garder l'ancienne quantité pour l'historique
        double ancienneQuantite = ligne.getQuantite();

        // Mettre à jour la quantité
        ligne.setQuantite(String.valueOf(quantite));
        demandeFilleService.saveDemandeFille(ligne);

        // Recalculer le total de la demande mère
        DemandeMere demande = ligne.getDemandeMere();
        List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        // Recalcul du montantEstime de la ligne modifiée
        if (ligne.getPrixUnitaire() != null && ligne.getPrixUnitaire() > 0) {
            ligne.setMontantEstime(quantite * ligne.getPrixUnitaire());
        } else {
            ligne.setMontantEstime(null);
        }
        demandeFilleService.saveDemandeFille(ligne);

        // Recalcul du totalEstime sur la demande mère
        double newTotalEstime = lignes.stream()
                .filter(l -> l.getPrixUnitaire() != null && l.getPrixUnitaire() > 0)
                .mapToDouble(l -> {
                    double qte = (l.getId() == ligneId) ? quantite : l.getQuantite();
                    return qte * l.getPrixUnitaire();
                })
                .sum();

        demande.setTotalEstime(newTotalEstime > 0 ? newTotalEstime : null);
        demandeMereService.saveDemandeMere(demande);

        // Historique de la modification
        String designation = ligne.getArticle() != null ? ligne.getArticle().getDesignation() : "Article inconnu";
        String codeArticle = ligne.getArticle() != null ? ligne.getArticle().getCodeArticle() : "N/A";

        validationDemandeService.journaliser(demande, current, demande.getStatut(),
                "Modification de quantité — " + codeArticle + " - " + designation
                        + " | Ancienne quantité : " + ancienneQuantite
                        + " → Nouvelle quantité : " + quantite);

        return ResponseEntity.ok(Map.of(
                "quantite", quantite,
                "totalEstime", newTotalEstime
        ));
    }
    @GetMapping("/fiche/{id}")
    public String demandeFiche(@PathVariable("id") String id,
                               Model model,
                               @RequestParam(required = false, defaultValue = "/demande/list") String returnUrl,
                               RedirectAttributes redirectAttributes, HttpSession session) {

        // Lien de retour : uniquement une page interne des demandes (pas de redirection externe)
        if (returnUrl == null || !returnUrl.startsWith("/demande/")
                || returnUrl.contains("//") || returnUrl.contains("\\")) {
            returnUrl = "/demande/list";
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        String decisionToken = UUID.randomUUID().toString();
        session.setAttribute("decisionToken_" + id, decisionToken);
        model.addAttribute("decisionToken", decisionToken);

        boolean isMG         = hasRole(auth, "ROLE_MOYENS_GENERAUX");
        boolean isControleur = hasRole(auth, "ROLE_CONTROLEUR");
        boolean isDFC        = hasRole(auth, "ROLE_DFC");
        boolean isSG         = hasRole(auth, "ROLE_SG");
        boolean isAdminOrSpecial = WorkflowService.estBackoffice(auth);

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable : " + id);
            return "redirect:/demande/list";
        }

        if (!workflowService.aAccesDemande(demande, current, auth)) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé à cette demande.");
            return "redirect:/demande/list";
        }

        DroitsDecision droits = workflowService.calculerDroits(demande, current, auth);
        boolean estValidateurAssigne      = droits.estValidateurAssigne();
        boolean isViewerNplus1OfDemandeur = droits.estNPlus1();

        boolean canDecisionN1         = droits.n1();
        boolean canDecisionMG         = droits.mg();
        boolean canDecisionControleur = droits.controleur();
        boolean canDecisionDFC        = droits.dfc();
        boolean canDecisionSG         = droits.sg();
        boolean canDecisionCodep      = droits.codep();
        boolean isCodepWorkflow = Boolean.TRUE.equals(demande.getViaCodep());

        boolean isValidatedCodep = demande.getStatut() == StatutDemande.VALIDE && demande.getDecisionViaCodep(isCodepWorkflow);

        // Message spécifique pour les validateurs assignés
        String statutHint = null;

        boolean peutModifierBrouillon = DemandeBrouillonService.peutModifier(demande, current);
        model.addAttribute("estBrouillon", demande.estBrouillon());
        model.addAttribute("peutModifierBrouillon", peutModifierBrouillon);

        if (demande.estBrouillon()) {
            statutHint = "Brouillon : la demande n'est visible que par vous. Modifiez-la si besoin, puis soumettez-la pour lancer la validation.";
        } else if (estValidateurAssigne && demande.getStatut() == StatutDemande.CREE) {
            statutHint = "Vous êtes validateur assigné pour cette demande. Action requise.";
        } else if (isViewerNplus1OfDemandeur && demande.getStatut() == StatutDemande.VALIDATION_N1) {
            statutHint = "Vous avez validé — en attente de traitement par les Moyens Généraux.";
        } else if (isMG) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N1) {
                statutHint = "Demande en attente de votre validation (Moyens Généraux).";
            } else if (demande.getStatut() == StatutDemande.VALIDATION_N2) {
                statutHint = "Vous avez validé — en attente de traitement par le contrôleur de gestion.";
            }
        } else if (isControleur) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N2) {
                statutHint = "Demande en attente de votre validation (contrôleur de gestion).";
            } else if (demande.getStatut() == StatutDemande.VALIDATION_N3) {
                statutHint = "Vous avez validé — en attente de validation finale (D.F.C.).";
            }
        } else if (isDFC) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N3) {
                statutHint = "Demande en attente de votre validation (D.F.C.).";
            } else if (demande.getStatut() == StatutDemande.VALIDATION_N4) {
                statutHint = "Vous avez validé — en attente de validation finale (S.G.).";
            }
        } else if (isSG) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N4) {
                statutHint = "Demande en attente de votre validation finale (S.G.).";
            } else if (demande.getStatut() == StatutDemande.VALIDE) {
                statutHint = "Demande finalisée.";
            }
        }

        // Lignes
        List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        // Libellés, badges et frise : source unique EtapeWorkflow
        Map<Integer, String> statutLabels = EtapeWorkflow.libellesAttente();
        String statutLabel  = statutLabels.getOrDefault(demande.getStatut(), "INCONNU");
        TypeDemande typeCircuit = demande.getTypeDemandeEffectif();
        Map<Integer, String> stepLabels   = EtapeWorkflow.libellesEtapeFranchie(isCodepWorkflow, typeCircuit);
        Map<Integer, String> histoLabels  = EtapeWorkflow.libellesValidateur();
        Map<Integer, String> badgeClasses = EtapeWorkflow.badgesFiche();
        Map<Integer, String> badgeIcons   = EtapeWorkflow.icones();
        List<String> steps = EtapeWorkflow.libellesCircuit(isCodepWorkflow, typeCircuit);
        int currentStep    = EtapeWorkflow.numeroDansCircuit(demande.getStatut(), isCodepWorkflow, typeCircuit);

        List<DemandePieceJointe> piecesJointes = demandePieceJointeService.listByDemandeId(demande.getId());
        List<CodepPieceJointe> codepPiecesJointes = codepPieceJointeService.listByDemandeId(demande.getId());
        CommentaireFinance commentaireFinance = commentaireFinanceService.getCommentaireFinanceByIdDemande(demande);
        List<HistoriqueFinanceDemande> historiquesFinance = historiqueFinanceDemandeService.getHistoriqueByDemande(demande);

        List<ValidationDemande> historiques = validationDemandeService.getHistorique(demande);
        CentreBudgetaire[] ligneBudgetaires = centreBudgetaireService.getAllCentreBudgetaires();
        ModeTraitement[] modeTraitements = modeTraitementService.getAllTraitements();
        // Demande Autre : une seule dépense, dont le montant a été saisi par le demandeur
        boolean estAutre = typeCircuit.isDepenseAutre();
        model.addAttribute("estAutre", estAutre);
        // Mode de traitement fixé par le type : les M.G. ne le choisissent plus (sauf anciennes demandes)
        model.addAttribute("modeTraitementImpose", typeCircuit.getModeTraitement());
        model.addAttribute("detailAutre", estAutre ? demandeAutreService.getDetail(demande).orElse(null) : null);

        boolean canVoirPrix = isAdminOrSpecial || isViewerNplus1OfDemandeur || estAutre;
        model.addAttribute("canVoirPrix", canVoirPrix);

        model.addAttribute("steps", steps);
        model.addAttribute("historiques", historiques);
        model.addAttribute("historiquesFinance", historiquesFinance);
        model.addAttribute("piecesJointes", piecesJointes);
        model.addAttribute("codepPiecesJointes", codepPiecesJointes);
        model.addAttribute("currentStep", currentStep);
        model.addAttribute("isRefused", demande.getStatut() == StatutDemande.REFUSE);
        model.addAttribute("isValidated", demande.getStatut() == StatutDemande.VALIDE);
        model.addAttribute("isCodepWorkflow", isCodepWorkflow);
        model.addAttribute("isValidatedCodep", isValidatedCodep);
        model.addAttribute("histoLabels", histoLabels);

        // Model (IMPORTANT : toujours envoyer les booléens)
        model.addAttribute("demande", demande);
        model.addAttribute("lignes", lignes);
        model.addAttribute("devises", deviseService.getAllDevises());

        model.addAttribute("canDecisionN1", canDecisionN1);
        model.addAttribute("canDecisionMG", canDecisionMG);
        model.addAttribute("canDecisionControleur", canDecisionControleur);
        model.addAttribute("canDecisionDFC", canDecisionDFC);
        model.addAttribute("canDecisionCodep", canDecisionCodep);
        model.addAttribute("canDecisionSG",  canDecisionSG);

        model.addAttribute("statutLabels", statutLabels);
        model.addAttribute("statutLabel", statutLabel);
        model.addAttribute("statutHint", statutHint);
        model.addAttribute("stepLabels", stepLabels);
        model.addAttribute("badgeClasses", badgeClasses);
        model.addAttribute("badgeIcons", badgeIcons);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());
        model.addAttribute("typesDemande", TypeDemande.values());
        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
        model.addAttribute("modeTraitement", modeTraitements);
        model.addAttribute("ligneBudgetaires", ligneBudgetaires);
        model.addAttribute("commentaireFinance", commentaireFinance);

        model.addAttribute("estValidateurAssigne", estValidateurAssigne);
        model.addAttribute("isViewerNplus1OfDemandeur", isViewerNplus1OfDemandeur);
        model.addAttribute("returnUrl", returnUrl);

        boolean canTransmitFinance = WorkflowService.peutTransmettreFinance(auth)
                && demande.getStatutTransmissionFinance() == DemandeMere.StatutTransmissionFinance.A_TRANSMETTRE;
        model.addAttribute("canTransmitFinance", canTransmitFinance);
        boolean isFinance = hasFinanceAccess(auth);
        boolean canPayFinance = isFinance
                && demande.getStatutTransmissionFinance() == DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE;
        model.addAttribute("canPayFinance", canPayFinance);
        // Bons de commande de la demande (lecture seule) : créés depuis la demande (ancien circuit)
        // ou bons groupés qui contiennent au moins une de ses lignes
        model.addAttribute("bonsCommande", bonCommandeService.getBonsCommandeDeLaDemande(demande));
        model.addAttribute("lignesCommandees", bonCommandeService.getLignesCommandeesDeLaDemande(demande));


        // ── Bon de sortie ────────────────────────────────────────────────────────
        // Seules les demandes de fourniture portent sur des articles en stock
        // La saisie se fait sur sa propre page (/bon-sortie/nouveau) ; la fiche n'en garde que le lien
        boolean canCreateBS = isMG && BonSortieService.peutCreerBonSortie(demande);

        model.addAttribute("canCreateBS", canCreateBS);
        model.addAttribute("bonsSortie", bonSortieService.getBonSortieByDemande(demande));
        // ── Fin Bon de sortie ────────────────────────────────────────────────────



        return "demande/demande-fiche";
    }




    @PostMapping("/fiche/{id}/decision")
    public String decision(@PathVariable("id") String id,
                           @RequestParam("decision") String decision,
                           @RequestParam(value = "natureDemande", required = false) String natureDemande,
                           @RequestParam(value = "modeTraitement", required = false) String modeTraitement,
                           @RequestParam(value = "commentaire", required = false) String commentaire,
                           @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
                           @RequestParam(name = "ligneBudgetaire", required = false) String ligneBudgetaire,
                           @RequestParam(name = "commentaireControleur" , required = false) String commentaireControleur,
                           @RequestParam(value = "priorite", required = false) String priorite,
                           @RequestParam(value = "submissionToken") String submissionToken,
                           HttpSession session,
                           RedirectAttributes redirectAttributes)  {
        String redirectFiche = "redirect:/demande/fiche/" + id;

        // ── Token anti double-soumission ────────────────────────────────────────
        String sessionKey   = "decisionToken_" + id;
        String sessionToken = (String) session.getAttribute(sessionKey);
        if (sessionToken == null || !sessionToken.equals(submissionToken)) {
            redirectAttributes.addFlashAttribute("ko",
                    "Cette décision a déjà été soumise. Veuillez vérifier l'état de la demande.");
            return redirectFiche;
        }
        session.removeAttribute(sessionKey); // consommer immédiatement

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur current = utilisateurCourant(auth);
        DecisionDemande cmd = new DecisionDemande(commentaire, piecesJointes, natureDemande, priorite,
                modeTraitement, ligneBudgetaire, commentaireControleur);
        String action = (decision == null) ? "" : decision.trim().toUpperCase();

        try {
            String message = switch (action) {
                case "APPROVE" -> workflowService.approuver(demande, current, auth, cmd);
                case "REJECT"  -> {
                    workflowService.rejeter(demande, current, auth, cmd);
                    yield "Demande rejetée.";
                }
                default -> {
                    log.warn("Décision invalide reçue pour la demande {} : [{}]", id, decision);
                    throw new WorkflowException("Décision invalide.");
                }
            };
            redirectAttributes.addFlashAttribute("ok", message);
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return redirectFiche;
    }

    /**
     * Liste unique des paiements directs (Moyens Généraux + Finance).
     * Les statuts visibles dépendent du rôle :
     *  - MG / Admin : à transmettre, transmise à la finance, payée
     *  - Finance (lecture) : transmise à la finance, payée
     */
    @GetMapping("/paiements-directs")
    public String listePaiementsDirects(
            Model model,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0")           int page,
            @RequestParam(defaultValue = "10")          int size,
            @RequestParam(defaultValue = "dateDemande") String sort,
            @RequestParam(defaultValue = "desc")        String dir,
            @RequestParam(required = false)             String   statutTransmission,
            @RequestParam(required = false)             String   priorite,
            @RequestParam(required = false)             String   motif,
            @RequestParam(required = false)             String   num,
            @RequestParam(required = false)             String   demandeur,
            @RequestParam(required = false)             String   type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<DemandeMere.StatutTransmissionFinance> visibles = statutsPaiementDirectVisibles(auth);
        if (visibles.isEmpty()) {
            return "redirect:/error/403";
        }

        // Un statut demandé hors du périmètre du rôle est ignoré
        DemandeMere.StatutTransmissionFinance statutChoisi = null;
        if (statutTransmission != null && !statutTransmission.isBlank()) {
            try {
                statutChoisi = DemandeMere.StatutTransmissionFinance.valueOf(statutTransmission.trim());
            } catch (IllegalArgumentException ignored) {}
            if (!visibles.contains(statutChoisi)) statutChoisi = null;
        }
        statutTransmission = statutChoisi == null ? null : statutChoisi.name();

        DemandeMereSpec.SearchCriteria criteria = DemandeMereSpec.SearchCriteria.builder()
                .num(num)
                .demandeur(demandeur)
                .type(type)
                .priorite(priorite)
                .motif(motif)
                .statut(StatutDemande.VALIDE)
                .paiementDirectOnly(true)
                .statutTransmissionFinance(statutChoisi)
                .statutsTransmissionFinance(statutChoisi == null ? visibles : null)
                .dateFrom(DemandeMereService.toFrom(dateFrom))
                .dateTo(DemandeMereService.toTo(dateTo))
                .build();

        Page<DemandeMere> mePage = demandeMereService.search(criteria, page, size, sort, dir);

        Map<String, String> transmissionFiltre = new LinkedHashMap<>();
        for (DemandeMere.StatutTransmissionFinance st : visibles) {
            switch (st) {
                case A_TRANSMETTRE     -> transmissionFiltre.put(st.name(), "À transmettre");
                case TRANSMISE_FINANCE -> transmissionFiltre.put(st.name(), "Transmise à la finance");
                case PAYEE             -> transmissionFiltre.put(st.name(), "Payée");
                default -> {}
            }
        }
        model.addAttribute("transmissionFiltre", transmissionFiltre);

        Map<String, String> prioriteFiltre = new LinkedHashMap<>();
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P2), "P2");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P1), "P1");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P0), "P0");
        model.addAttribute("prioriteFiltre", prioriteFiltre);

        model.addAttribute("demandesMeres", mePage);
        model.addAttribute("statutTransmission", statutTransmission == null ? "" : statutTransmission);
        model.addAttribute("priorite",  priorite  == null ? "" : priorite);
        model.addAttribute("motif",     motif     == null ? "" : motif);
        model.addAttribute("num",       num       == null ? "" : num);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("type",      type      == null ? "" : type);
        model.addAttribute("dateFrom",  dateFrom);
        model.addAttribute("dateTo",    dateTo);
        model.addAttribute("page",      mePage.getNumber());
        model.addAttribute("size",      size);
        model.addAttribute("sort",      sort);
        model.addAttribute("dir",       dir);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));
        String qs = request.getQueryString();
        model.addAttribute("returnUrl", "/demande/paiements-directs" + (qs == null ? "" : "?" + qs));

        return "demande/paiements-directs-liste";
    }

    /** Ancienne URL de la vue Finance : redirige vers la liste unique en conservant les filtres. */
    @GetMapping("/paiements-directs-finance")
    public String listePaiementsDirectsFinance(HttpServletRequest request) {
        String qs = request.getQueryString();
        return "redirect:/demande/paiements-directs" + (qs == null ? "" : "?" + qs);
    }

    private static List<DemandeMere.StatutTransmissionFinance> statutsPaiementDirectVisibles(Authentication auth) {
        if (WorkflowService.peutTransmettreFinance(auth) || hasRole(auth, "ROLE_MOYENS_GENERAUX")) {
            return List.of(
                    DemandeMere.StatutTransmissionFinance.A_TRANSMETTRE,
                    DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE,
                    DemandeMere.StatutTransmissionFinance.PAYEE);
        }
        if (hasFinanceReadAccess(auth)) {
            return List.of(
                    DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE,
                    DemandeMere.StatutTransmissionFinance.PAYEE);
        }
        return List.of();
    }

    @PostMapping("/fiche/{id}/transmettre-finance")
    public String transmettreFinance(@PathVariable String id,
                                     @RequestParam(required = false) String commentaire,
                                     RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!WorkflowService.peutTransmettreFinance(auth)) {
            redirectAttributes.addFlashAttribute("ko", "Action réservée au validateur des Moyens Généraux.");
            return "redirect:/demande/fiche/" + id;
        }
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());
        DemandeMere demande = demandeMereService.getDemandeById(id);
        if (demande == null || !demandeMereService.transmettreAFinance(demande, current, commentaire)) {
            redirectAttributes.addFlashAttribute("ko", "Cette demande ne peut pas être transmise à la finance.");
            return "redirect:/demande/fiche/" + id;
        }
        historiqueFinanceDemandeService.logTransmissionFinance(demande, current, commentaire);

        List<Utilisateur> financeUsers = utilisateurService.getUtilisateursByRole("FINANCE");
        ess.envoyerMailTransmissionFinance(demande, current, commentaire, financeUsers);

        redirectAttributes.addFlashAttribute("ok", "Demande transmise à la finance.");
        return "redirect:/demande/fiche/" + id;
    }

    @PostMapping("/fiche/{id}/payer-finance")
    public String payerFinance(@PathVariable String id,
                               @RequestParam(required = false) String commentaire,
                               RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!hasFinanceAccess(auth)) {
            redirectAttributes.addFlashAttribute("ko", "Action réservée à la finance.");
            return "redirect:/demande/fiche/" + id;
        }

        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());
        DemandeMere demande = demandeMereService.getDemandeById(id);
        if (demande == null || !demandeMereService.marquerCommePayee(demande, current, commentaire)) {
            redirectAttributes.addFlashAttribute("ko", "Cette demande ne peut pas être marquée comme payée.");
            return "redirect:/demande/fiche/" + id;
        }
        historiqueFinanceDemandeService.logPaiementEffectue(demande, current, commentaire);

        List<Utilisateur> financeUsers = utilisateurService.getUtilisateursByRole("FINANCE");
        ess.envoyerMailPaiementEffectue(demande, current, commentaire, financeUsers);

        redirectAttributes.addFlashAttribute("ok", "Demande marquée comme payée.");
        return "redirect:/demande/fiche/" + id;
    }

    @PostMapping("/fiche/{id}/prix")
    @ResponseBody
    public ResponseEntity<?> savePrix(
            @PathVariable("id") String id,
            @RequestBody Map<String, Double> prix,
            HttpServletRequest request) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isMG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isMG && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Accès refusé."));
        }

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Demande introuvable."));
        }

        if (demande.getStatut() != StatutDemande.VALIDATION_N1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "La saisie des prix n'est autorisée qu'à l'étape Validation MG."));
        }

        // Mise à jour des prix ligne par ligne
        List<String> erreurs = new ArrayList<>();
        for (Map.Entry<String, Double> entry : prix.entrySet()) {
            int ligneId;
            try {
                ligneId = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                erreurs.add("ID invalide : " + entry.getKey());
                continue;
            }
            Double valeur = entry.getValue();
            if (valeur == null || valeur <= 0) {
                erreurs.add("Prix invalide pour la ligne " + ligneId);
                continue;
            }
            DemandeFille ligne = demandeFilleService.getDemandeFilleById(ligneId);
            if (ligne == null || !ligne.getDemandeMere().getId().equals(id)) {
                erreurs.add("Ligne introuvable : " + ligneId);
                continue;
            }
            ligne.setPrixUnitaire(valeur); // déclenche aussi setMontantEstime via le setter
            demandeFilleService.saveDemandeFille(ligne);
        }

        // Recalcul du total estimé sur la DemandeMere
        List<DemandeFille> toutesLignes = demandeFilleService.getDemandeFilleByDemandeMere(demandeMereService.getDemandeMereById(id).orElseThrow());
        double total = toutesLignes.stream()
                .filter(l -> l.getPrixUnitaire() != null && l.getPrixUnitaire() > 0)
                .mapToDouble(DemandeFille::getMontantEstime)
                .sum();
        demande.setTotalEstime(total);
        demandeMereService.saveDemandeMere(demande);

        if (!erreurs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .body(Map.of("warning", erreurs, "totalEstime", total));
        }

        return ResponseEntity.ok(Map.of("ok", "Prix enregistrés.", "totalEstime", total));
    }

    @PostMapping("/fiche/{id}/send-codep")
    public String sendToCodep(@PathVariable("id") String id,
                              @RequestParam(value = "natureDemande", required = false) String natureDemande,
                              @RequestParam(value = "priorite", required = false) String prioriteParam,
                              @RequestParam(value = "modeTraitement", required = false) String modeTraitement,
                              @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
                              RedirectAttributes redirectAttributes) {

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            workflowService.envoyerAuCodep(demande, utilisateurCourant(auth), auth,
                    natureDemande, prioriteParam, modeTraitement, piecesJointes);
            redirectAttributes.addFlashAttribute("ok", "Demande envoyée au CODEP (action irréversible).");
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/demande/fiche/" + id;
    }
    // Aperçu — inline (images + PDF)
    @GetMapping("/files/{id}/{filename:.+}")
    public ResponseEntity<Resource> previewDemandeFile(@PathVariable String id,
                                                       @PathVariable String filename) throws IOException {
        return servirFichier(id, filename, "inline");
    }

    // Téléchargement — attachment forcé
    @GetMapping("/files/{id}/{filename:.+}/download")
    public ResponseEntity<Resource> downloadDemandeFile(@PathVariable String id,
                                                        @PathVariable String filename) throws IOException {
        return servirFichier(id, filename, "attachment");
    }

    /**
     * Sert une pièce jointe uniquement si elle appartient à la demande {@code id}
     * et si l'utilisateur a le droit de consulter cette demande.
     */
    private ResponseEntity<Resource> servirFichier(String id, String filename, String disposition) throws IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            return ResponseEntity.notFound().build();
        }
        if (!workflowService.aAccesDemande(demande, utilisateurCourant(auth), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!pieceJointeService.appartientALaDemande(id, filename)) {
            return ResponseEntity.notFound().build();
        }

        Resource file = storageService.loadAsResource(filename);
        String contentType = Files.probeContentType(file.getFile().toPath());
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(file);
    }

    private Utilisateur utilisateurCourant(Authentication auth) {
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        return utilisateurService.getUtilisateurByMail(principal.getMail());
    }

    @PostMapping("/fiche/{id}/pieces-jointes-mg")
    @ResponseBody
    public ResponseEntity<?> savePiecesJointesMG(
            @PathVariable("id") String id,
            @RequestParam("piecesJointes") MultipartFile[] piecesJointes,
            HttpServletRequest request) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur current = utilisateurCourant(auth);

        if (!hasRole(auth, "ROLE_MOYENS_GENERAUX") && !hasRole(auth, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Accès refusé."));
        }

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Demande introuvable."));
        }

        if (demande.getStatut() != StatutDemande.VALIDATION_N1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "L'ajout de pièces jointes n'est autorisé qu'à l'étape Validation MG."));
        }

        if (piecesJointes == null || piecesJointes.length == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Aucun fichier reçu."));
        }

        List<String> nomsSauvegardes = new ArrayList<>();
        List<String> erreurs = new ArrayList<>();

        for (MultipartFile f : piecesJointes) {
            if (f == null || f.isEmpty()) continue;

            if (!PieceJointeWorkflowService.formatAutorise(f)) {
                erreurs.add("Format refusé : " + f.getOriginalFilename() + " (PDF et images JPEG, PNG, GIF, WebP uniquement).");
                continue;
            }
            pieceJointeService.enregistrerUnePourDemande(f, demande, "PJ_MG");
            nomsSauvegardes.add(f.getOriginalFilename());
        }

        // Une seule entrée d'historique pour tous les fichiers
        validationDemandeService.journaliserPiecesJointes(demande, current, demande.getStatut(), nomsSauvegardes, "MG");

        if (!erreurs.isEmpty() && nomsSauvegardes.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", String.join(", ", erreurs)));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("sauvegardes", nomsSauvegardes);
        if (!erreurs.isEmpty()) result.put("warnings", erreurs);

        return ResponseEntity.ok(result);
    }



    @GetMapping("/stock/disponible/{codeArticle}")
    @ResponseBody
    public ResponseEntity<?> getStockDisponible(
            @PathVariable String codeArticle,
            @RequestParam(required = false) String demandeId) {

        // Stock des lots FIFO moins le stock réservé par toutes les demandes de matériel
        // (demandeId est conservé pour compatibilité de l'appel, la réservation est globale)
        double stockPhysique = lotStockService.getStockDisponible(codeArticle);
        double quantiteReservee = reservationStockService.quantiteReservee(codeArticle, List.of());
        double stockDisponible = Math.max(0, stockPhysique - quantiteReservee);

        // Prix FIFO : prix du premier lot disponible
        double prixFifo = 0;
        try {
            if (stockDisponible > 0) {
                prixFifo = lotStockService.getPrixFifo(codeArticle, 1);
            }
        } catch (Exception e) {
            // Fallback sur le dernier prix connu
            prixFifo = prixArticleService
                    .getDernierPrixByArticle(codeArticle)
                    .map(PrixArticle::getPrixUnitaire)
                    .orElse(0.0);
        }

        log.debug("[STOCK FIFO] code={} | stockPhysique={} | reserved={} | disponible={} | prixFifo={}",
                codeArticle, stockPhysique, quantiteReservee, stockDisponible, prixFifo);

        return ResponseEntity.ok(Map.of(
                "stockDisponible",  stockDisponible,
                "quantiteReservee", quantiteReservee,
                "dernierPrix",      prixFifo
        ));
    }

    @PostMapping("/ligne/{id}/scinder")
    @ResponseBody
    public ResponseEntity<?> scinderLigne(
            @PathVariable int id,
            @RequestBody ScinderDTO dto) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        DemandeFille ligneOriginale = demandeFilleService.getDemandeFilleById(id);
        if (ligneOriginale == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ligne introuvable."));

        // Scission réservée aux MG, à l'étape MG
        if (!workflowService.calculerDroits(ligneOriginale.getDemandeMere(), utilisateurCourant(auth), auth).mg())
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Accès refusé."));

        // Contrôles (quantité, stock libre toutes demandes) et écritures en une transaction
        try {
            approvisionnementLigneService.scinder(id, dto.getQteStock(), dto.getPrixStock());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Remet une ligne servie depuis le stock à l'achat (stock promis disparu, par exemple).
     * Moyens Généraux : à leur étape de validation, ou sur une demande validée.
     */
    @PostMapping("/ligne/{id}/remettre-achat")
    public String remettreLigneEnAchat(@PathVariable int id,
                                       @RequestParam(name = "retour", required = false) String retour,
                                       RedirectAttributes redirectAttributes) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        DemandeFille ligne = demandeFilleService.getDemandeFilleById(id);
        if (ligne == null) {
            redirectAttributes.addFlashAttribute("ko", "Ligne introuvable.");
            return "redirect:/demande/list";
        }
        DemandeMere demande = ligne.getDemandeMere();
        // Depuis la saisie d'un bon de sortie, on y revient ; sinon, fiche de la demande
        String fiche = "bon-sortie".equals(retour)
                ? "redirect:/bon-sortie/nouveau?demande=" + demande.getId()
                : "redirect:/demande/fiche/" + demande.getId();

        boolean etapeMG = workflowService.calculerDroits(demande, utilisateurCourant(auth), auth).mg();
        boolean mgSurDemandeValidee = demande.getStatut() == StatutDemande.VALIDE
                && hasRole(auth, "ROLE_MOYENS_GENERAUX");
        if (!etapeMG && !mgSurDemandeValidee) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé.");
            return fiche;
        }

        try {
            approvisionnementLigneService.remettreEnAchat(id);
            redirectAttributes.addFlashAttribute("ok", "La ligne " + ligne.getArticle().getCodeArticle()
                    + " est remise à l'achat : sa réservation de stock est libérée"
                    + (demande.getStatut() == StatutDemande.VALIDE
                       ? " et le reste à sortir apparaît dans les lignes à acheter." : "."));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return fiche;
    }

    @GetMapping("/ligne/{ligneId}/stock-disponible")
    @ResponseBody
    public ResponseEntity<?> getStockDisponiblePourLigne(@PathVariable Integer ligneId) {
        DemandeFille ligne = demandeFilleService.getDemandeFilleById(ligneId);
        if (ligne == null) {
            return ResponseEntity.notFound().build();
        }

        if (ligne.getTypeApprovisionnement() != DemandeFille.TypeApprovisionnement.STOCK) {
            return ResponseEntity.ok(Map.of("stockDisponible", Double.MAX_VALUE));
        }

        String codeArticle = ligne.getArticle().getCodeArticle();
        double stockPhysique = lotStockService.getStockDisponible(codeArticle);

        // Réservations de toutes les autres lignes STOCK, toutes demandes de matériel confondues
        double totalReserveAutres = reservationStockService.quantiteReservee(codeArticle, List.of(ligneId));

        double stockDisponible = Math.max(0, stockPhysique - totalReserveAutres);

        return ResponseEntity.ok(Map.of(
                "stockDisponible", stockDisponible,
                "stockPhysique", stockPhysique,
                "totalReserveAutres", totalReserveAutres
        ));
    }

}
