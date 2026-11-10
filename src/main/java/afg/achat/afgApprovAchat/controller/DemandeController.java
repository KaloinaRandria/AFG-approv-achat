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

    private final afg.achat.afgApprovAchat.service.paiement.PaiementDirectService paiementDirectService;

    @GetMapping("/add")
    public String addDemandePage(Model model, HttpServletRequest request, HttpSession session) {

        // Token anti double-soumission
        String token = UUID.randomUUID().toString();
        session.setAttribute("submissionToken", token);
        model.addAttribute("submissionToken", token);

        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
        model.addAttribute("ligneBudgetaires", centreBudgetaireService.getAllCentreBudgetaires());

        return "demande/demande-saisie";
    }
    @PostMapping("/save")
    public String insertDemande(@RequestParam(name = "dateSortie") String dateSortie,
                                @RequestParam(name = "motif") String motif,
                                @RequestParam(name = "description") String description,
                                @RequestParam(name = "articleCodes[]") List<String> articleCodes,
                                @RequestParam(name = "quantite[]") List<String> quantite,
                                @RequestParam(name = "priorite") String priorite,
                                @RequestParam(name = "piecesJointes") MultipartFile[] piecesJointes,
                                @RequestParam(name = "submissionToken") String submissionToken,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        // ── Vérification token anti double-soumission ────────────────────────────
        String sessionToken = (String) session.getAttribute("submissionToken");

        if (sessionToken == null || !sessionToken.equals(submissionToken)) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    "Cette demande a déjà été soumise. Veuillez vérifier la liste des demandes.");
            return "redirect:/demande/list";
        }

        // Consommer le token immédiatement — toute soumission suivante sera bloquée
        session.removeAttribute("submissionToken");


        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(user.getMail());

        try {
            if (dateSortie == null || dateSortie.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "La date prévue pour la livraison est obligatoire.");
                return "redirect:/demande/add";
            }
            if (motif == null || motif.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "Le motif evoqué est obligatoire.");
                return "redirect:/demande/add";
            }

            if (articleCodes == null || articleCodes.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "Impossible de valider la demande sans aucune ligne d’article.");
                return "redirect:/demande/add";
            }

            double totalGeneral = 0.0;
            List<DemandeFille> lignes = new ArrayList<>();

// Boucle unique — calcul total + préparation des lignes
            for (int i = 0; i < articleCodes.size(); i++) {
                String code = articleCodes.get(i);
                Article article = articleService.getArticleByCodeArticle(code)
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable : " + code));

                double qte = MontantCalculator.parseDoubleSafe(quantite.get(i));
                if (qte <= 0) continue;

                // Prix issu de PrixArticle (dernier BL reçu pour cet article)
                double prix = prixArticleService.getDernierPrixByArticle(code)
                        .map(PrixArticle::getPrixUnitaire)
                        .orElse(0.0);

                totalGeneral += qte * prix;

                DemandeFille demandeFille = new DemandeFille();
                demandeFille.setArticle(article);
                demandeFille.setQuantite(quantite.get(i));
                demandeFille.setStatut(1);
                demandeFille.setPrixUnitaire(prix); // snapshot issu de PrixArticle
                lignes.add(demandeFille);
            }

            // Contrôles AVANT tout enregistrement : sinon une demande incomplète
            // serait créée puis resoumise (doublon).
            if (lignes.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "Impossible de valider la demande : aucune ligne n'a une quantité supérieure à 0.");
                return "redirect:/demande/add";
            }
            String erreurPj = verifierFormatsPiecesJointes(piecesJointes);
            if (erreurPj != null) {
                redirectAttributes.addFlashAttribute("ko", erreurPj);
                return "redirect:/demande/add";
            }

            DemandeMere demandeMere = new DemandeMere();
            demandeMere.setId(idGenerator);
            demandeMere.setDateDemande(String.valueOf(LocalDateTime.now()));
            demandeMere.setDateSortie(dateSortie + "T00:00");
            demandeMere.setPriorite(DemandeMere.PrioriteDemande.valueOf(priorite.trim()));
            demandeMere.setMotifEvoque(motif);
            demandeMere.setDemandeur(utilisateur);
            demandeMere.setDescription(description);
            demandeMere.setStatut(1);
            demandeMere.setTotalPrix(totalGeneral); //total basé sur PrixArticle
            demandeMere.setTotalEstime(totalGeneral);
            this.demandeMereService.saveDemandeMere(demandeMere);

            // Save les lignes (après demandeMere pour que la FK soit valide)
            for (DemandeFille ligne : lignes) {
                ligne.setDemandeMere(demandeMere); //FK settée après persist
                this.demandeFilleService.saveDemandeFille(ligne);
            }
            sauvegarderPiecesJointesDecision(piecesJointes, demandeMere, "");

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
                String baseUrl = "http://10.25.10.151:8081/AFG-approv-achat";
//                String baseUrl = "http://localhost:8080";



                String lienValidation = baseUrl + "/demande/fiche/" + demandeMere.getId();
                propsSup.put("lienValidation", lienValidation);

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
                    String baseUrl = "http://10.25.10.151:8081/AFG-approv-achat";
                    String lienValidation = baseUrl + "/demande/fiche/" + demandeMere.getId();
                    propsValidateur.put("lienValidation", lienValidation);

                    Mail mailValidateur = new Mail(
                            "validSup",
                            validateur.getMail(),
                            "[AFG Bank - Demande Achat] - Action requise : Validation de la demande N°" + demandeMere.getId(),
                            propsValidateur
                    );
                    ess.sendEmail(mailValidateur);
                }
            }


            redirectAttributes.addFlashAttribute("ok", "Demande enregistrée avec succès.");
            return "redirect:/demande/list";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/demande/add";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'enregistrement : " + e.getMessage());
            return "redirect:/demande/add";
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
                .priorite(priorite)
                .motif(motif)
                .statut(statutFilter)
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
                statut, priorite, motif, num, demandeur, type,
                dateFrom, dateTo, scope,
                size, sort, dir,
                isBackoffice, hasChildren,
                isMG, isControleur, isDFC, isSG, isAdmin
        );
        String returnUrl = buildReturnUrl(page, size, sort, dir, statut, priorite, motif, num, demandeur, type, dateFrom, dateTo, scope);
        model.addAttribute("returnUrl", returnUrl);

        model.addAttribute("motif",                  motif);
        model.addAttribute("nbUtilisateursAValider",  idsAValider.size());
        model.addAttribute("hasDemandesAValider",     !idsAValider.isEmpty());
        model.addAttribute("StatutValide",            StatutDemande.VALIDE);

        return "demande/demande-liste";
    }

// ── Helpers privés ───────────────────────────────────────────────────────────

    // ── Helpers privés ────────────────────────────────────────────────────────

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    private boolean hasFinanceAccess(Authentication auth) {
        return hasRole(auth, "ROLE_FINANCE")
                || hasRole(auth, "ROLE_ADMIN");
    }

    private boolean hasFinanceReadAccess(Authentication auth) {
        return hasRole(auth, "ROLE_FINANCE")
                || hasRole(auth, "ROLE_FINANCE_CONSULT")
                || hasRole(auth, "ROLE_ADMIN");
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
                               String demandeur, String type,
                               LocalDate dateFrom, LocalDate dateTo,
                               String scope, int size, String sort, String dir,
                               boolean isBackofficeValidator, boolean hasChildren,
                               boolean isMG, boolean isControleur,
                               boolean isDFC, boolean isSG, boolean isAdmin) {

        // Statut labels (tableau)
        model.addAttribute("statutLabels", Map.of(
                StatutDemande.CREE,           "En attente N+1",
                StatutDemande.VALIDATION_N1,  "En attente M.G.",
                StatutDemande.VALIDATION_N2,  "En attente Contrôle de gestion",
                StatutDemande.VALIDATION_N3,  "En attente D.F.C.",
                StatutDemande.VALIDATION_N4,  "En attente S.G.",
                StatutDemande.DECISION_CODEP, "En attente CODEP",
                StatutDemande.VALIDE,         "VALIDÉE",
                StatutDemande.REFUSE,         "REFUSÉE"
        ));

        // Statut filtre (select)
        Map<Integer, String> statutFiltre = new LinkedHashMap<>();
        statutFiltre.put(StatutDemande.CREE,           "En attente N+1");
        statutFiltre.put(StatutDemande.VALIDATION_N1,  "En attente M.G.");
        statutFiltre.put(StatutDemande.VALIDATION_N2,  "En attente Contrôle de gestion");
        statutFiltre.put(StatutDemande.VALIDATION_N3,  "En attente D.F.C.");
        statutFiltre.put(StatutDemande.VALIDATION_N4,  "En attente S.G.");
        statutFiltre.put(StatutDemande.DECISION_CODEP, "En attente CODEP");
        statutFiltre.put(StatutDemande.VALIDE,         "VALIDÉE");
        statutFiltre.put(StatutDemande.REFUSE,         "REFUSÉE");
        model.addAttribute("statutFiltre", statutFiltre);

        // Badge classes
        Map<Integer, String> badgeClasses = new HashMap<>();
        badgeClasses.put(StatutDemande.CREE,           "badge-grey");
        badgeClasses.put(StatutDemande.VALIDATION_N1,  "badge-blue");
        badgeClasses.put(StatutDemande.VALIDATION_N2,  "badge-light-blue");
        badgeClasses.put(StatutDemande.VALIDATION_N3,  "badge-purple");
        badgeClasses.put(StatutDemande.VALIDATION_N4,  "badge-green-soft");
        badgeClasses.put(StatutDemande.DECISION_CODEP, "badge-orange");
        badgeClasses.put(StatutDemande.VALIDE,         "badge-green");
        badgeClasses.put(StatutDemande.REFUSE,         "badge-red");
        model.addAttribute("badgeClasses", badgeClasses);

        // Badge icons
        Map<Integer, String> badgeIcons = new HashMap<>();
        badgeIcons.put(StatutDemande.CREE,           "fa-user-clock");
        badgeIcons.put(StatutDemande.VALIDATION_N1,  "fa-clipboard-check");
        badgeIcons.put(StatutDemande.VALIDATION_N2,  "fa-coins");
        badgeIcons.put(StatutDemande.VALIDATION_N3,  "fa-gavel");
        badgeIcons.put(StatutDemande.VALIDATION_N4,  "fa-stamp");
        badgeIcons.put(StatutDemande.DECISION_CODEP, "fa-landmark");
        badgeIcons.put(StatutDemande.VALIDE,         "fa-check-circle");
        badgeIcons.put(StatutDemande.REFUSE,         "fa-times-circle");
        model.addAttribute("badgeIcons", badgeIcons);

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
        int colCount = 5; // id + date + motif + priorité + statut
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
                                  String num, String demandeur, String type,
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
        if (!calculerDroitsDecision(demande, current, auth).auMoinsUn()
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
        DroitsDecision droits = calculerDroitsDecision(ligne.getDemandeMere(), current, auth);
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
            String codeArticle = ligne.getArticle().getCodeArticle();
            double stockDisponible = lotStockService.getStockDisponible(codeArticle);

            // Récupérer la demande mère pour connaître les autres lignes du même article
            DemandeMere demande = ligne.getDemandeMere();
            List<DemandeFille> autresLignesStock = demandeFilleService.getDemandeFilleByDemandeMere(demande)
                    .stream()
                    .filter(l -> l.getId() != ligneId)  // Exclure la ligne en cours
                    .filter(l -> l.getTypeApprovisionnement() == DemandeFille.TypeApprovisionnement.STOCK)
                    .filter(l -> l.getArticle().getCodeArticle().equals(codeArticle))
                    .toList();

            // Calculer la quantité totale déjà réservée par d'autres lignes stock du même article
            double totalReserveAutres = autresLignesStock.stream()
                    .mapToDouble(DemandeFille::getQuantite)
                    .sum();

            // Stock réellement disponible pour CETTE ligne
            double stockReelDisponible = stockDisponible - totalReserveAutres;

            if (quantite > stockReelDisponible) {
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

        ValidationDemande historique = new ValidationDemande();
        historique.setDemandeMere(demande);
        historique.setValidateur(current);
        historique.setEtape(demande.getStatut());
        historique.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
        historique.setCommentaire(
                "Modification de quantité — " + codeArticle + " - " + designation
                        + " | Ancienne quantité : " + ancienneQuantite
                        + " → Nouvelle quantité : " + quantite
        );
        historique.setDateAction(String.valueOf(LocalDateTime.now()));

        validationDemandeService.logAction(historique);

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
        boolean isAdminOrSpecial = estBackoffice(auth);

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable : " + id);
            return "redirect:/demande/list";
        }

        if (!aAccesDemande(demande, current, auth)) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé à cette demande.");
            return "redirect:/demande/list";
        }

        DroitsDecision droits = calculerDroitsDecision(demande, current, auth);
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

        if (estValidateurAssigne && demande.getStatut() == StatutDemande.CREE) {
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

        // Labels
        Map<Integer, String> statutLabels = new LinkedHashMap<>();
        statutLabels.put(StatutDemande.CREE, "En attente N+1");
        statutLabels.put(StatutDemande.VALIDATION_N1, "En attente M.G.");
        statutLabels.put(StatutDemande.VALIDATION_N2, "En attente Contrôleur");
        statutLabels.put(StatutDemande.VALIDATION_N3, "En attente D.F.C.");
        statutLabels.put(StatutDemande.VALIDATION_N4,  "En attente S.G.");
        statutLabels.put(StatutDemande.DECISION_CODEP,"En attente CODEP");
        statutLabels.put(StatutDemande.VALIDE, "VALIDÉE");
        statutLabels.put(StatutDemande.REFUSE, "REFUSÉE");

        String statutLabel = statutLabels.getOrDefault(demande.getStatut(), "INCONNU");

        Map<Integer, String> stepLabels = new HashMap<>();

        stepLabels.put(StatutDemande.CREE, "Création");
        stepLabels.put(StatutDemande.VALIDATION_N1, "N+1");
        stepLabels.put(StatutDemande.VALIDATION_N2, "Moyens Généraux");
        stepLabels.put(StatutDemande.VALIDATION_N3, "Contrôleur de gestion");
        stepLabels.put(StatutDemande.VALIDATION_N4,  "D.F.C.");
        stepLabels.put(StatutDemande.DECISION_CODEP, "Comité de dépense");
        stepLabels.put(StatutDemande.REFUSE, "Refus");

        Map<Integer, String> histoLabels = new HashMap<>();
        histoLabels.put(StatutDemande.CREE,          "N+1");
        histoLabels.put(StatutDemande.VALIDATION_N1, "Moyens Généraux");
        histoLabels.put(StatutDemande.VALIDATION_N2, "Contrôleur de gestion");
        histoLabels.put(StatutDemande.VALIDATION_N3, "D.F.C.");
        histoLabels.put(StatutDemande.VALIDATION_N4,  "S.G.");
        histoLabels.put(StatutDemande.DECISION_CODEP,"Comité de dépense");



// Cas particulier : VALIDE dépend du workflow
        if (isCodepWorkflow) {
            stepLabels.put(StatutDemande.VALIDE, "Comité de dépense");
        } else {
            stepLabels.put(StatutDemande.VALIDE, "S.G.");
        }


        Map<Integer, String> badgeClasses = new HashMap<>();
        Map<Integer, String> badgeIcons = new HashMap<>();

        badgeClasses.put(StatutDemande.CREE,           "badge-wait");    // gris/orange — en attente
        badgeClasses.put(StatutDemande.VALIDATION_N1,  "badge-info");    // bleu — MG
        badgeClasses.put(StatutDemande.VALIDATION_N2,  "badge-purple");  // violet — Contrôleur
        badgeClasses.put(StatutDemande.VALIDATION_N3,  "badge-warning"); // jaune/orange — DFC
        badgeClasses.put(StatutDemande.VALIDATION_N4,  "badge-teal");    // teal — SG
        badgeClasses.put(StatutDemande.DECISION_CODEP, "badge-codep");   // couleur dédiée CODEP
        badgeClasses.put(StatutDemande.VALIDE,         "badge-success"); // vert — validé
        badgeClasses.put(StatutDemande.REFUSE,         "badge-danger");  // rouge — refusé

        badgeIcons.put(StatutDemande.CREE, "fa-user-clock");
        badgeIcons.put(StatutDemande.VALIDATION_N1, "fa-clipboard-check");
        badgeIcons.put(StatutDemande.VALIDATION_N2, "fa-coins");
        badgeIcons.put(StatutDemande.VALIDATION_N3, "fa-gavel");
        badgeIcons.put(StatutDemande.VALIDATION_N4,   "fa-stamp");
        badgeIcons.put(StatutDemande.VALIDE, "fa-check-circle");
        badgeIcons.put(StatutDemande.REFUSE, "fa-times-circle");


        List<String> steps;
        if (isCodepWorkflow) {
            steps = List.of(
                    "Demande créée",
                    "Supérieur hiérarchique (N+1)",
                    "Moyens Généraux",
                    "Comité de dépense",
                    "Contrôleur de gestion",
                    "D.F.C.",
                    "S.G."
            );
        } else {
            steps = List.of(
                    "Demande créée",
                    "Supérieur hiérarchique (N+1)",
                    "Moyens Généraux",
                    "Contrôleur de gestion",
                    "D.F.C.",
                    "S.G."
            );
        }



        List<DemandePieceJointe> piecesJointes = demandePieceJointeService.listByDemandeId(demande.getId());
        List<CodepPieceJointe> codepPiecesJointes = codepPieceJointeService.listByDemandeId(demande.getId());
        CommentaireFinance commentaireFinance = commentaireFinanceService.getCommentaireFinanceByIdDemande(demande); // ← AJOUTER
        List<HistoriqueFinanceDemande> historiquesFinance = historiqueFinanceDemandeService.getHistoriqueByDemande(demande);


        int currentStep;
        if (isCodepWorkflow) {
            currentStep = switch (demande.getStatut()) {
                case StatutDemande.CREE           -> 1;
                case StatutDemande.VALIDATION_N1  -> 2;  // MG
                case StatutDemande.DECISION_CODEP -> 3;  // CODEP
                case StatutDemande.VALIDATION_N2  -> 4;  // Contrôleur
                case StatutDemande.VALIDATION_N3  -> 5;  // DFC
                case StatutDemande.VALIDATION_N4  -> 6;  // SG
                case StatutDemande.VALIDE         -> 7;
                case StatutDemande.REFUSE         -> -1;
                default -> 1;
            };
        } else {
            currentStep = switch (demande.getStatut()) {
                case StatutDemande.CREE          -> 1;
                case StatutDemande.VALIDATION_N1 -> 2;
                case StatutDemande.VALIDATION_N2 -> 3;
                case StatutDemande.VALIDATION_N3 -> 4;
                case StatutDemande.VALIDATION_N4 -> 5;
                case StatutDemande.VALIDE        -> 6;
                case StatutDemande.REFUSE        -> -1;
                default -> 1;
            };
        }

        List<ValidationDemande> historiques = validationDemandeService.getHistorique(demande);
        CentreBudgetaire[] ligneBudgetaires = centreBudgetaireService.getAllCentreBudgetaires();
        ModeTraitement[] modeTraitements = modeTraitementService.getAllTraitements();
        boolean canVoirPrix = isAdminOrSpecial || isViewerNplus1OfDemandeur;
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
        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
        model.addAttribute("modeTraitement", modeTraitements);
        model.addAttribute("ligneBudgetaires", ligneBudgetaires);
        model.addAttribute("commentaireFinance", commentaireFinance);

        model.addAttribute("estValidateurAssigne", estValidateurAssigne);
        model.addAttribute("isViewerNplus1OfDemandeur", isViewerNplus1OfDemandeur);
        model.addAttribute("returnUrl", returnUrl);

        //Bon de Commande Creation
        boolean canCreateBC = demandeMereService.peutCreerBonCommande(demande,isMG);
        model.addAttribute("canCreateBC", canCreateBC);
        boolean canTransmitFinance = isMG
                && demande.getStatutTransmissionFinance() == DemandeMere.StatutTransmissionFinance.A_TRANSMETTRE;
        model.addAttribute("canTransmitFinance", canTransmitFinance);
        boolean isFinance = hasFinanceAccess(auth);
        boolean canPayFinance = isFinance
                && demande.getStatutTransmissionFinance() == DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE;
        model.addAttribute("canPayFinance", canPayFinance);
        model.addAttribute("bonsCommande", bonCommandeService.getBonCommandesByDemande(demande));


        // ── Bon de sortie ────────────────────────────────────────────────────────
        boolean canCreateBS = demande.getStatut() == StatutDemande.VALIDE
                && isMG
                && demande.getEtatLivraison() != DemandeMere.EtatLivraison.SOLDEE;

        model.addAttribute("canCreateBS", canCreateBS);
        model.addAttribute("bonsSortie", bonSortieService.getBonSortieByDemande(demande));

        if (canCreateBS) {
            List<LigneRestanteDTO> lignesRestantes = demandeFilleService
                    .getDemandeFilleByDemandeMere(demande)
                    .stream()
                    .filter(df -> df.getStatut() != StatutDemande.REFUSE)
                    .map(df -> {
                        double restant         = bonSortieService.getQuantiteRestante(df);
                        double totalSorti      = df.getQuantite() - restant;
                        double stockDisponible = stockFilleRepo.getStockDisponible(
                                df.getArticle().getCodeArticle()
                        ); // ← SUM(entree) - SUM(sortie)
                        return new LigneRestanteDTO(df, totalSorti, restant, stockDisponible);
                    })
                    .filter(l -> l.getRestant() > 0)
                    .toList();

            model.addAttribute("lignesRestantes", lignesRestantes);
        }
        // ── Fin Bon de sortie ────────────────────────────────────────────────────



        return "demande/demande-fiche";
    }




    @PostMapping("/fiche/{id}/decision")
    public String decision(@PathVariable("id") String id,
                           @RequestParam("decision") String decision,
                           @RequestParam(value = "typeDemande", required = false) String typeDemande,
                           @RequestParam(value = "modeTraitement", required = false) String modeTraitement,
                           @RequestParam(value = "commentaire", required = false) String commentaire,
                           @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
                           @RequestParam(name = "ligneBudgetaire", required = false) String ligneBudgetaire,
                           @RequestParam(name = "commentaireControleur" , required = false) String commentaireControleur,
                           @RequestParam(value = "submissionToken") String submissionToken,
                           HttpSession session,
                           RedirectAttributes redirectAttributes, HttpServletRequest request)  {
        // ── Token anti double-soumission ────────────────────────────────────────
        String sessionKey   = "decisionToken_" + id;
        String sessionToken = (String) session.getAttribute(sessionKey);

        if (sessionToken == null || !sessionToken.equals(submissionToken)) {
            redirectAttributes.addFlashAttribute("ko",
                    "Cette décision a déjà été soumise. Veuillez vérifier l'état de la demande.");
            return "redirect:/demande/fiche/" + id;
        }
        session.removeAttribute(sessionKey); // consommer immédiatement

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());
        boolean isAdmin = hasRole(auth, "ROLE_ADMIN");

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        String redirectFiche = "redirect:/demande/fiche/" + id;
        String action = (decision == null) ? "" : decision.trim().toUpperCase();

        DroitsDecision droits = calculerDroitsDecision(demande, current, auth);
        if (!isAdmin && !droits.auMoinsUn()) {
            redirectAttributes.addFlashAttribute("ko", "Cette demande ne peut pas être traitée par vous.");
            return redirectFiche;
        }

        String cmt = (commentaire == null) ? "" : commentaire.trim();

        // Formats des pièces jointes vérifiés AVANT toute écriture
        String erreurPj = verifierFormatsPiecesJointes(piecesJointes);
        if (erreurPj != null) {
            redirectAttributes.addFlashAttribute("ko", erreurPj);
            return redirectFiche;
        }

        // ----- REJECT : commentaire obligatoire -----
        if ("REJECT".equals(action)) {

            if (cmt.isBlank()) {
                redirectAttributes.addFlashAttribute("ko",
                        "Le commentaire est obligatoire pour rejeter une demande.");
                return redirectFiche;
            }
            sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_REFUS");

            int etapeCourante = demande.getStatut();

            ValidationDemande h = new ValidationDemande();
            h.setDemandeMere(demande);
            h.setValidateur(current);
            h.setEtape(etapeCourante);
            h.setDecision(ValidationDemande.DecisionValidation.REFUSE);
            h.setCommentaire(cmt);
            h.setDateAction(String.valueOf(LocalDateTime.now()));

            validationDemandeService.logAction(h);

            // ensuite seulement changer statut
            demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.REFUSE);

            Map<String, Object> props = new HashMap<>();
            props.put("id",            demande.getId());
            props.put("demandeur",     demande.getDemandeur());
            props.put("validateur",    current);
            props.put("commentaire",   cmt);
            props.put("dateDecision",  LocalDateTime.now()
                    .format(DateTimeFormatter
                            .ofPattern("dd/MM/yyyy HH:mm")));
            props.put("etape",        StatutDemande.getLibelle(etapeCourante));

            Mail mail = new Mail(
                    "refusDemande",
                    demande.getDemandeur().getMail(),
                    "[AFG/MADA] - Votre demande a été refusée",
                    props
            );
            ess.sendEmail(mail);

            redirectAttributes.addFlashAttribute("ok", "Demande rejetée.");
            return redirectFiche;
        }


        // ----- APPROVE : transition selon le niveau -----
        if ("APPROVE".equals(action)) {

            if (droits.n1()) {
                int etape = demande.getStatut();
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_N1");
                journaliserPiecesJointes(demande, current, etape, pjAjoutees, "");

                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N1);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                notifierValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N1, "MOYENS_GENERAUX");

                redirectAttributes.addFlashAttribute("ok", "Demande envoyée en validation N1 (MG).");
                return redirectFiche;
            }

            if (droits.codep()) {
                int etape = demande.getStatut();
                List<String> pjAjoutees = sauvegarderPiecesJointesCodep(piecesJointes, demande, "PV_CODEP");
                journaliserPiecesJointes(demande, current, etape, pjAjoutees, "CODEP");

                demandeMereService.recalculerTotal(demande);
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                notifierValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N2, "CONTROLEUR");

                redirectAttributes.addFlashAttribute("ok", "Demande validée par le Comité de dépense.");
                return redirectFiche;
            }

            if (droits.mg()) {
                int etape = demande.getStatut();

                // ── 1. Contrôles (aucune écriture tant qu'ils ne passent pas) ──
                String erreurPrix = verifierPrixLignes(demande, "Impossible de valider");
                if (erreurPrix != null) {
                    redirectAttributes.addFlashAttribute("ko", erreurPrix);
                    return redirectFiche;
                }

                DemandeMere.NatureDemande nature;
                try {
                    nature = parseNature(typeDemande);
                } catch (IllegalArgumentException ex) {
                    redirectAttributes.addFlashAttribute("ko", ex.getMessage());
                    return redirectFiche;
                }

                DemandeMere.PrioriteDemande nouvellePriorite;
                try {
                    nouvellePriorite = parsePriorite(request.getParameter("priorite"));
                } catch (IllegalArgumentException ex) {
                    redirectAttributes.addFlashAttribute("ko", ex.getMessage());
                    return redirectFiche;
                }

                if (modeTraitement == null || modeTraitement.isBlank()) {
                    redirectAttributes.addFlashAttribute("ko", "Le mode de traitement est obligatoire.");
                    return redirectFiche;
                }
                ModeTraitement mt = null;
                try {
                    mt = modeTraitementService.getModeTraitementById(Integer.parseInt(modeTraitement.trim()));
                } catch (NumberFormatException ignored) {
                    // mt reste null
                }
                if (mt == null) {
                    redirectAttributes.addFlashAttribute("ko", "Mode de traitement invalide : " + modeTraitement);
                    return redirectFiche;
                }

                // ── 2. Écritures ──
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_MG");
                journaliserPiecesJointes(demande, current, etape, pjAjoutees, "");

                demande.setNatureDemande(nature);
                appliquerPriorite(demande, current, nouvellePriorite);

                demande.setModeTraitement(mt);
                journaliser(demande, current, etape, "Ajout du mode de traitement : " + mt.getLibelle());

                demandeMereService.recalculerTotal(demande);
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                notifierValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N2, "CONTROLEUR");

                redirectAttributes.addFlashAttribute("ok", "Demande validée par les Moyens Généraux (N2).");
                return redirectFiche;
            }

            if (droits.controleur()) {
                int etape = demande.getStatut();

                // ── 1. Contrôles ──
                if (ligneBudgetaire == null || ligneBudgetaire.isBlank()) {
                    redirectAttributes.addFlashAttribute("ko", "Ligne budgétaire obligatoire pour le contrôleur de gestion.");
                    return redirectFiche;
                }
                if (commentaireControleur == null || commentaireControleur.isBlank()) {
                    redirectAttributes.addFlashAttribute("ko", "Le commentaire du contrôleur de gestion est obligatoire.");
                    return redirectFiche;
                }
                CentreBudgetaire centreBudgetaire = null;
                try {
                    centreBudgetaire = centreBudgetaireService.getCentreBudgetaireById(Integer.parseInt(ligneBudgetaire.trim()));
                } catch (NumberFormatException ignored) {
                    // centreBudgetaire reste null
                }
                if (centreBudgetaire == null) {
                    redirectAttributes.addFlashAttribute("ko", "Ligne budgétaire invalide : " + ligneBudgetaire);
                    return redirectFiche;
                }

                // ── 2. Écritures ──
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_CONTROLEUR");
                journaliserPiecesJointes(demande, current, etape, pjAjoutees, "");

                demande.setCentreBudgetaire(centreBudgetaire);

                CommentaireFinance commentaireFinance = new CommentaireFinance();
                commentaireFinance.setCommentateur(current);
                commentaireFinance.setDemandeMere(demande);
                commentaireFinance.setCommentaire(commentaireControleur.trim());
                commentaireFinanceService.insertCommentaireFinance(commentaireFinance);

                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N3);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                notifierValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N3, "DFC");

                redirectAttributes.addFlashAttribute("ok", "Demande validée par le contrôleur de gestion (N3).");
                return redirectFiche;
            }

            if (droits.dfc()) {
                int etape = demande.getStatut();
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_DFC");
                journaliserPiecesJointes(demande, current, etape, pjAjoutees, "");

                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N4);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                notifierValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N4, "SG");

                redirectAttributes.addFlashAttribute("ok", "Demande validée par la D.F.C., transmise au S.G.");
                return redirectFiche;
            }

            if (droits.sg()) {
                int etape = demande.getStatut();
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_SG");
                journaliserPiecesJointes(demande, current, etape, pjAjoutees, "");

                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDE);
                demandeMereService.preparerTransmissionFinance(demande);
                validationDemandeService.logValidation(demande, current, cmt, etape);

                ess.envoyerMailDemandeur(demande, current, cmt, etape, StatutDemande.VALIDE);
                ess.envoyerMailValidateur(demande, current, etape, StatutDemande.VALIDE, null);

                redirectAttributes.addFlashAttribute("ok", "Demande validée et finalisée par le S.G.");
                return redirectFiche;
            }

            // Admin : passage forcé à l'étape suivante
            if (isAdmin) {
                int etape = demande.getStatut();
                int next = switch (etape) {
                    case StatutDemande.CREE           -> StatutDemande.VALIDATION_N1;
                    case StatutDemande.VALIDATION_N1  -> StatutDemande.VALIDATION_N2;
                    case StatutDemande.DECISION_CODEP -> StatutDemande.VALIDATION_N2;
                    case StatutDemande.VALIDATION_N2  -> StatutDemande.VALIDATION_N3;
                    case StatutDemande.VALIDATION_N3  -> StatutDemande.VALIDATION_N4;
                    case StatutDemande.VALIDATION_N4  -> StatutDemande.VALIDE;
                    default -> etape;
                };
                if (next == etape) {
                    redirectAttributes.addFlashAttribute("ko", "Cette demande est déjà clôturée.");
                    return redirectFiche;
                }
                demandeMereService.appliquerDecisionGlobale(demande, next);
                if (next == StatutDemande.VALIDE) {
                    demandeMereService.preparerTransmissionFinance(demande);
                }
                validationDemandeService.logValidation(demande, current, cmt, etape);
                redirectAttributes.addFlashAttribute("ok", "Statut mis à jour (ADMIN).");
                return redirectFiche;
            }
        }
        log.warn("Décision invalide reçue pour la demande {} : [{}]", id, decision);
        redirectAttributes.addFlashAttribute("ko", "Décision invalide.");
        return redirectFiche;
    }

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
        if (!hasRole(auth, "ROLE_MOYENS_GENERAUX") && !hasRole(auth, "ROLE_ADMIN")) {
            return "redirect:/error/403";
        }

        DemandeMere.StatutTransmissionFinance singleTransmission = null;
        List<DemandeMere.StatutTransmissionFinance> allowedTransmissions = null;
        if (statutTransmission != null && !statutTransmission.isBlank()) {
            try {
                singleTransmission = DemandeMere.StatutTransmissionFinance.valueOf(statutTransmission.trim());
            } catch (Exception ignored) {}
        }
        if (singleTransmission == null) {
            allowedTransmissions = List.of(
                    DemandeMere.StatutTransmissionFinance.A_TRANSMETTRE,
                    DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE,
                    DemandeMere.StatutTransmissionFinance.PAYEE
            );
        }

        DemandeMereSpec.SearchCriteria criteria = DemandeMereSpec.SearchCriteria.builder()
                .num(num)
                .demandeur(demandeur)
                .type(type)
                .priorite(priorite)
                .motif(motif)
                .statut(StatutDemande.VALIDE)
                .paiementDirectOnly(true)
                .statutTransmissionFinance(singleTransmission)
                .statutsTransmissionFinance(allowedTransmissions)
                .dateFrom(DemandeMereService.toFrom(dateFrom))
                .dateTo(DemandeMereService.toTo(dateTo))
                .build();

        Page<DemandeMere> mePage = demandeMereService.search(criteria, page, size, sort, dir);

        populatePaiementDirectModel(
                model, mePage, statutTransmission, priorite, motif, num, demandeur, type,
                dateFrom, dateTo, size, sort, dir,
                List.of(
                        DemandeMere.StatutTransmissionFinance.A_TRANSMETTRE,
                        DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE,
                        DemandeMere.StatutTransmissionFinance.PAYEE
                )
        );

        String returnUrl = buildPaiementDirectReturnUrl("/demande/paiements-directs", page, size, sort, dir,
                statutTransmission, priorite, motif, num, demandeur, type, dateFrom, dateTo);
        model.addAttribute("returnUrl", returnUrl);

        return "demande/paiements-directs-liste";
    }

    @GetMapping("/paiements-directs-finance")
    public String listePaiementsDirectsFinance(
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
        if (!hasFinanceReadAccess(auth)) {
            return "redirect:/error/403";
        }

        DemandeMere.StatutTransmissionFinance singleTransmission = null;
        List<DemandeMere.StatutTransmissionFinance> allowedTransmissions = null;
        if (statutTransmission != null && !statutTransmission.isBlank()) {
            try {
                singleTransmission = DemandeMere.StatutTransmissionFinance.valueOf(statutTransmission.trim());
            } catch (Exception ignored) {}
        }
        if (singleTransmission == null) {
            allowedTransmissions = List.of(
                    DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE,
                    DemandeMere.StatutTransmissionFinance.PAYEE
            );
        }

        DemandeMereSpec.SearchCriteria criteria = DemandeMereSpec.SearchCriteria.builder()
                .num(num)
                .demandeur(demandeur)
                .type(type)
                .priorite(priorite)
                .motif(motif)
                .statut(StatutDemande.VALIDE)
                .paiementDirectOnly(true)
                .statutTransmissionFinance(singleTransmission)
                .statutsTransmissionFinance(allowedTransmissions)
                .dateFrom(DemandeMereService.toFrom(dateFrom))
                .dateTo(DemandeMereService.toTo(dateTo))
                .build();

        Page<DemandeMere> mePage = demandeMereService.search(criteria, page, size, sort, dir);

        populatePaiementDirectModel(
                model, mePage, statutTransmission, priorite, motif, num, demandeur, type,
                dateFrom, dateTo, size, sort, dir,
                List.of(
                        DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE,
                        DemandeMere.StatutTransmissionFinance.PAYEE
                )
        );

        String returnUrl = buildPaiementDirectReturnUrl("/demande/paiements-directs-finance", page, size, sort, dir,
                statutTransmission, priorite, motif, num, demandeur, type, dateFrom, dateTo);
        model.addAttribute("returnUrl", returnUrl);

        return "demande/paiements-directs-finance";
    }

    private void populatePaiementDirectModel(
            Model model, Page<DemandeMere> demandesMeres,
            String statutTransmission, String priorite, String motif, String num,
            String demandeur, String type,
            LocalDate dateFrom, LocalDate dateTo,
            int size, String sort, String dir,
            List<DemandeMere.StatutTransmissionFinance> allowedStatuts) {

        Map<String, String> transmissionFiltre = new LinkedHashMap<>();
        for (DemandeMere.StatutTransmissionFinance st : allowedStatuts) {
            switch (st) {
                case A_TRANSMETTRE -> transmissionFiltre.put("A_TRANSMETTRE", "À transmettre");
                case TRANSMISE_FINANCE -> transmissionFiltre.put("TRANSMISE_FINANCE", "Transmise à la finance");
                case PAYEE -> transmissionFiltre.put("PAYEE", "Payée");
            }
        }
        model.addAttribute("transmissionFiltre", transmissionFiltre);

        Map<String, String> prioriteFiltre = new LinkedHashMap<>();
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P2), "P2");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P1), "P1");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P0), "P0");
        model.addAttribute("prioriteFiltre", prioriteFiltre);

        model.addAttribute("demandesMeres", demandesMeres);
        model.addAttribute("demandes", demandesMeres.getContent());
        model.addAttribute("statutTransmission", statutTransmission == null ? "" : statutTransmission);
        model.addAttribute("priorite",  priorite  == null ? "" : priorite);
        model.addAttribute("motif",     motif     == null ? "" : motif);
        model.addAttribute("num",       num       == null ? "" : num);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("type",      type      == null ? "" : type);
        model.addAttribute("dateFrom",  dateFrom);
        model.addAttribute("dateTo",    dateTo);
        model.addAttribute("page",      demandesMeres.getNumber());
        model.addAttribute("size",      size);
        model.addAttribute("sort",      sort);
        model.addAttribute("dir",       dir);
    }

    private String buildPaiementDirectReturnUrl(
            String path, int page, int size, String sort, String dir,
            String statutTransmission, String priorite, String motif,
            String num, String demandeur, String type,
            LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder sb = new StringBuilder(path).append("?");
        sb.append("page=").append(page);
        sb.append("&size=").append(size);
        sb.append("&sort=").append(sort);
        sb.append("&dir=").append(dir);
        if (statutTransmission != null) sb.append("&statutTransmission=").append(statutTransmission);
        if (priorite != null)   sb.append("&priorite=").append(priorite);
        if (motif   != null)    sb.append("&motif=").append(motif);
        if (num     != null)    sb.append("&num=").append(num);
        if (demandeur != null)  sb.append("&demandeur=").append(demandeur);
        if (type    != null)    sb.append("&type=").append(type);
        if (dateFrom != null)   sb.append("&dateFrom=").append(dateFrom);
        if (dateTo  != null)    sb.append("&dateTo=").append(dateTo);
        return sb.toString();
    }

    @PostMapping("/fiche/{id}/transmettre-finance")
    public String transmettreFinance(@PathVariable String id,
                                     @RequestParam(required = false) String commentaire,
                                     RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!hasRole(auth, "ROLE_MOYENS_GENERAUX") && !hasRole(auth, "ROLE_ADMIN")) {
            redirectAttributes.addFlashAttribute("ko", "Action réservée aux Moyens Généraux.");
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
                              @RequestParam(value = "typeDemande", required = false) String typeDemande,
                              @RequestParam(value = "priorite", required = false) String prioriteParam,
                              @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
                              RedirectAttributes redirectAttributes) {

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        // Vérification : seule la validation MG peut envoyer au CODEP
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());
        boolean isMG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));

        if (!isMG || demande.getStatut() != StatutDemande.VALIDATION_N1) {
            redirectAttributes.addFlashAttribute("ko", "Vous ne pouvez pas envoyer cette demande au CODEP.");
            return "redirect:/demande/fiche/" + id;
        }

        // ── 1. Contrôles (aucune écriture tant qu'ils ne passent pas) ──
        String erreur = verifierPrixLignes(demande, "Impossible d'envoyer au CODEP");
        if (erreur == null) erreur = verifierFormatsPiecesJointes(piecesJointes);
        if (erreur != null) {
            redirectAttributes.addFlashAttribute("ko", erreur);
            return "redirect:/demande/fiche/" + id;
        }

        DemandeMere.NatureDemande nature;
        DemandeMere.PrioriteDemande nouvellePriorite;
        try {
            nature = parseNature(typeDemande);
            nouvellePriorite = parsePriorite(prioriteParam);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("ko", ex.getMessage());
            return "redirect:/demande/fiche/" + id;
        }

        // ── 2. Écritures ──
        int etape = demande.getStatut();
        demande.setNatureDemande(nature);
        appliquerPriorite(demande, current, nouvellePriorite);
        demande.setViaCodep(true);

        List<String> pjAjoutees = sauvegarderPiecesJointesCodep(piecesJointes, demande, "PJ_CODEP");
        journaliserPiecesJointes(demande, current, etape, pjAjoutees, "");

        demandeMereService.recalculerTotal(demande);
        demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.DECISION_CODEP);
        validationDemandeService.logValidation(demande, current, "Envoi au CODEP", etape);
        redirectAttributes.addFlashAttribute("ok", "Demande envoyée au CODEP (action irréversible).");

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
        if (!aAccesDemande(demande, utilisateurCourant(auth), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean appartientALaDemande =
                demandePieceJointeService.listByDemandeId(id).stream()
                        .anyMatch(pj -> filename.equals(pj.getStoredName()))
                || codepPieceJointeService.listByDemandeId(id).stream()
                        .anyMatch(pj -> filename.equals(pj.getStoredName()));
        if (!appartientALaDemande) {
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

    // ── Pièces jointes ───────────────────────────────────────────────────────

    /** Formats acceptés pour toutes les pièces jointes (images courantes et PDF). */
    private static final Set<String> MIME_AUTORISES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    private static boolean formatAutorise(MultipartFile f) {
        return f.getContentType() != null && MIME_AUTORISES.contains(f.getContentType());
    }

    /** @return un message d'erreur si un fichier n'est pas autorisé, sinon {@code null}. */
    private static String verifierFormatsPiecesJointes(MultipartFile[] piecesJointes) {
        if (piecesJointes == null) return null;
        for (MultipartFile f : piecesJointes) {
            if (f == null || f.isEmpty()) continue;
            if (!formatAutorise(f)) {
                return "Fichier refusé : '" + f.getOriginalFilename()
                        + "'. Seuls les images (JPEG, PNG, GIF, WebP) et PDF sont autorisés.";
            }
        }
        return null;
    }

    private String stockerFichier(MultipartFile f, DemandeMere demande, String prefixRef) {
        if (!formatAutorise(f)) {
            throw new IllegalArgumentException("Fichier refusé : " + f.getOriginalFilename());
        }
        String safeDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String ref = (prefixRef == null || prefixRef.isEmpty() ? "" : prefixRef + "_")
                + demande.getId()
                + "_" + demande.getDemandeur().getNom()
                + "_" + demande.getDemandeur().getPrenom()
                + "_" + safeDate;
        return storageService.store(f, ref);
    }

    /** Enregistre des pièces jointes de demande. Les formats doivent avoir été vérifiés avant. */
    private List<String> sauvegarderPiecesJointesDecision(MultipartFile[] piecesJointes,
                                                          DemandeMere demande,
                                                          String prefixRef) {
        if (piecesJointes == null) return List.of();
        List<String> nomsAjoutes = new ArrayList<>();
        for (MultipartFile f : piecesJointes) {
            if (f == null || f.isEmpty()) continue;

            DemandePieceJointe pj = new DemandePieceJointe();
            pj.setDemande(demande);
            pj.setOriginalName(f.getOriginalFilename());
            pj.setStoredName(stockerFichier(f, demande, prefixRef));
            pj.setContentType(f.getContentType());
            pj.setSize(f.getSize());
            pj.setUploadedAt(LocalDateTime.now());

            demandePieceJointeService.insert(pj);
            nomsAjoutes.add(f.getOriginalFilename());
        }
        return nomsAjoutes;
    }

    /** Enregistre des pièces jointes CODEP. Les formats doivent avoir été vérifiés avant. */
    private List<String> sauvegarderPiecesJointesCodep(MultipartFile[] piecesJointes,
                                                       DemandeMere demande,
                                                       String prefixRef) {
        if (piecesJointes == null) return List.of();
        List<String> nomsAjoutes = new ArrayList<>();
        for (MultipartFile f : piecesJointes) {
            if (f == null || f.isEmpty()) continue;

            CodepPieceJointe pj = new CodepPieceJointe();
            pj.setDemandeMere(demande);
            pj.setOriginalName(f.getOriginalFilename());
            pj.setStoredName(stockerFichier(f, demande, prefixRef));
            pj.setContentType(f.getContentType());
            pj.setSize(f.getSize());
            pj.setUploadedAt(LocalDateTime.now());

            codepPieceJointeService.insert(pj);
            nomsAjoutes.add(f.getOriginalFilename());
        }
        return nomsAjoutes;
    }

    // ── Historique ───────────────────────────────────────────────────────────

    private void journaliser(DemandeMere demande, Utilisateur auteur, int etape, String commentaire) {
        ValidationDemande h = new ValidationDemande();
        h.setDemandeMere(demande);
        h.setValidateur(auteur);
        h.setEtape(etape);
        h.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
        h.setCommentaire(commentaire);
        h.setDateAction(String.valueOf(LocalDateTime.now()));
        validationDemandeService.logAction(h);
    }

    private void journaliserPiecesJointes(DemandeMere demande, Utilisateur auteur, int etape,
                                          List<String> noms, String qualificatif) {
        if (noms.isEmpty()) return;
        String liste = noms.stream().map(n -> "• " + n).collect(Collectors.joining("\n"));
        String type = (qualificatif == null || qualificatif.isBlank()) ? "" : qualificatif + " ";
        journaliser(demande, auteur, etape,
                noms.size() + " pièce(s) jointe(s) " + type + "ajoutée(s) :\n" + liste);
    }

    // ── Règles métier partagées ──────────────────────────────────────────────

    /** @return un message d'erreur si une ligne non refusée n'a pas de prix, sinon {@code null}. */
    private String verifierPrixLignes(DemandeMere demande, String prefixeMessage) {
        List<String> lignesSansPrix = demandeFilleService.getDemandeFilleByDemandeMere(demande).stream()
                .filter(l -> l.getStatut() != StatutDemande.REFUSE)
                .filter(l -> l.getPrixUnitaire() == null || l.getPrixUnitaire() <= 0)
                .map(l -> l.getArticle() != null
                        ? l.getArticle().getCodeArticle() + " - " + l.getArticle().getDesignation()
                        : "Article inconnu")
                .toList();
        if (lignesSansPrix.isEmpty()) return null;
        return prefixeMessage + " : les articles suivants n'ont pas de prix unitaire : "
                + String.join(", ", lignesSansPrix);
    }

    /** OPEX/CAPEX obligatoire. */
    private static DemandeMere.NatureDemande parseNature(String typeDemande) {
        String td = (typeDemande == null) ? "" : typeDemande.trim().toUpperCase();
        if (td.isBlank()) {
            throw new IllegalArgumentException("Veuillez choisir le Type de demande (OPEX/CAPEX) avant de valider.");
        }
        try {
            return DemandeMere.NatureDemande.valueOf(td);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Type de demande invalide : " + typeDemande);
        }
    }

    /** Priorité facultative : {@code null} si non fournie. */
    private static DemandeMere.PrioriteDemande parsePriorite(String priorite) {
        if (priorite == null || priorite.isBlank()) return null;
        try {
            return DemandeMere.PrioriteDemande.valueOf(priorite.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Priorité invalide : " + priorite);
        }
    }

    /** Change la priorité (sans sauvegarder) et l'historise si elle a changé. */
    private void appliquerPriorite(DemandeMere demande, Utilisateur auteur,
                                   DemandeMere.PrioriteDemande nouvelle) {
        if (nouvelle == null || nouvelle == demande.getPriorite()) return;
        String ancienne = demande.getPriorite() != null ? demande.getPriorite().name() : "N/A";
        demande.setPriorite(nouvelle);
        journaliser(demande, auteur, demande.getStatut(),
                "Modification de priorité : " + ancienne + " → " + nouvelle.name());
    }

    /** Mail au demandeur + un mail à chaque utilisateur du rôle de l'étape suivante. */
    private void notifierValidation(DemandeMere demande, Utilisateur validateur, String commentaire,
                                    int etape, int prochaineEtape, String roleSuivant) {
        ess.envoyerMailDemandeur(demande, validateur, commentaire, etape, prochaineEtape);
        for (Utilisateur u : utilisateurService.getUtilisateursByRole(roleSuivant)) {
            ess.envoyerMailValidateur(demande, validateur, etape, prochaineEtape, u);
        }
    }

    // ── Droits ───────────────────────────────────────────────────────────────

    /**
     * Droits de décision de l'utilisateur sur une demande, à son étape actuelle.
     * Source unique utilisée par la fiche (affichage) et par les actions (contrôle).
     */
    private record DroitsDecision(boolean estNPlus1, boolean estValidateurAssigne,
                                  boolean n1, boolean mg, boolean controleur,
                                  boolean dfc, boolean sg, boolean codep) {
        boolean auMoinsUn() {
            return n1 || mg || controleur || dfc || sg || codep;
        }
    }

    private DroitsDecision calculerDroitsDecision(DemandeMere demande, Utilisateur current, Authentication auth) {
        Integer demandeurId = demande.getDemandeur() != null ? demande.getDemandeur().getId() : null;
        int statut = demande.getStatut();

        boolean estNPlus1 = demandeurId != null
                && !demandeurId.equals(current.getId())
                && utilisateurService.getIdsUtilisateurVisible(current.getId()).contains(demandeurId);
        boolean estValidateurAssigne = demandeurId != null
                && utilisateurService.getIdsUtilisateursAValider(current.getId()).contains(demandeurId);

        boolean isMG = hasRole(auth, "ROLE_MOYENS_GENERAUX");
        return new DroitsDecision(
                estNPlus1,
                estValidateurAssigne,
                statut == StatutDemande.CREE && (estNPlus1 || estValidateurAssigne),
                isMG && statut == StatutDemande.VALIDATION_N1,
                hasRole(auth, "ROLE_CONTROLEUR") && statut == StatutDemande.VALIDATION_N2,
                hasRole(auth, "ROLE_DFC") && statut == StatutDemande.VALIDATION_N3,
                hasRole(auth, "ROLE_SG") && statut == StatutDemande.VALIDATION_N4,
                isMG && statut == StatutDemande.DECISION_CODEP
        );
    }

    private boolean estBackoffice(Authentication auth) {
        return hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_AUDIT")
                || hasRole(auth, "ROLE_MOYENS_GENERAUX") || hasRole(auth, "ROLE_CONTROLEUR")
                || hasRole(auth, "ROLE_DFC") || hasRole(auth, "ROLE_SG");
    }

    /** Droit de consulter une demande (fiche et pièces jointes). */
    private boolean aAccesDemande(DemandeMere demande, Utilisateur current, Authentication auth) {
        if (estBackoffice(auth)) return true;

        // La finance consulte les paiements directs qui lui ont été transmis
        DemandeMere.StatutTransmissionFinance tf = demande.getStatutTransmissionFinance();
        if (hasFinanceReadAccess(auth)
                && (tf == DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE
                || tf == DemandeMere.StatutTransmissionFinance.PAYEE)) {
            return true;
        }

        Integer demandeurId = demande.getDemandeur() != null ? demande.getDemandeur().getId() : null;
        if (demandeurId == null) return false;
        return utilisateurService.getIdsUtilisateurVisible(current.getId()).contains(demandeurId)
                || utilisateurService.getIdsUtilisateursAValider(current.getId()).contains(demandeurId);
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
                    .body(Map.of("error", "L'ajout de pièces jointes n'est autorisé qu'à l'étape Validation MG."));
        }

        if (piecesJointes == null || piecesJointes.length == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Aucun fichier reçu."));
        }

        List<String> nomsSauvegardes = new ArrayList<>();
        List<String> erreurs = new ArrayList<>();

        //APRÈS — logAction une seule fois après la boucle
        for (MultipartFile f : piecesJointes) {
            if (f == null || f.isEmpty()) continue;

            if (!formatAutorise(f)) {
                erreurs.add("Format refusé : " + f.getOriginalFilename() + " (PDF et images JPEG, PNG, GIF, WebP uniquement).");
                continue;
            }

            DemandePieceJointe pj = new DemandePieceJointe();
            pj.setDemande(demande);
            pj.setOriginalName(f.getOriginalFilename());
            pj.setStoredName(stockerFichier(f, demande, "PJ_MG"));
            pj.setContentType(f.getContentType());
            pj.setSize(f.getSize());
            pj.setUploadedAt(LocalDateTime.now());

            demandePieceJointeService.insert(pj);
            nomsSauvegardes.add(f.getOriginalFilename()); // ← collecte seulement
        }

//Une seule entrée d'historique pour tous les fichiers
        if (!nomsSauvegardes.isEmpty()) {
            String listePj = nomsSauvegardes.stream()
                    .map(n -> "• " + n)
                    .collect(Collectors.joining("\n"));

            ValidationDemande histo = new ValidationDemande();
            histo.setDemandeMere(demande);
            histo.setValidateur(current);
            histo.setEtape(demande.getStatut());
            histo.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
            histo.setCommentaire(nomsSauvegardes.size() + " pièce(s) jointe(s) MG ajoutée(s) :\n" + listePj);
            histo.setDateAction(String.valueOf(LocalDateTime.now()));
            validationDemandeService.logAction(histo);
        }

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

        // Utiliser LotStock au lieu de StockFille
        double stockPhysique = lotStockService.getStockDisponible(codeArticle);

        double quantiteReservee = 0;
        if (demandeId != null && !demandeId.isBlank()) {
            Double reserved = demandeFilleService
                    .getQuantiteStockReserveePourDemande(demandeId, codeArticle);
            quantiteReservee = (reserved != null) ? reserved : 0;
        }

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

        System.out.println(">>> [STOCK FIFO] code=" + codeArticle
                + " | stockPhysique=" + stockPhysique
                + " | reserved=" + quantiteReservee
                + " | disponible=" + stockDisponible
                + " | prixFifo=" + prixFifo);

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
        if (!calculerDroitsDecision(ligneOriginale.getDemandeMere(), utilisateurCourant(auth), auth).mg())
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Accès refusé."));

        double qteTotale = ligneOriginale.getQuantite();
        double qteStock  = dto.getQteStock();
        double qteAchat  = qteTotale - qteStock;

        // Correction : > au lieu de >=
        if (qteStock <= 0 || qteStock > qteTotale)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Quantité stock invalide."));

        double prixFifo;
        try {
            prixFifo = lotStockService.getPrixFifo(
                    ligneOriginale.getArticle().getCodeArticle(), qteStock
            );
        } catch (Exception e) {
            prixFifo = dto.getPrixStock(); // fallback sur le prix envoyé par le JS
        }

        // Cas conversion totale
        if (qteStock == qteTotale) {
            ligneOriginale.setTypeApprovisionnement(DemandeFille.TypeApprovisionnement.STOCK);
            ligneOriginale.setPrixUnitaire(prixFifo);
            demandeFilleService.saveDemandeFille(ligneOriginale);
            demandeMereService.recalculerTotal(ligneOriginale.getDemandeMere());
            return ResponseEntity.ok(Map.of("ok", true));
        }

        // Scission normale
        ligneOriginale.setQuantite(String.valueOf(qteAchat));
        ligneOriginale.setTypeApprovisionnement(DemandeFille.TypeApprovisionnement.ACHAT);
        demandeFilleService.saveDemandeFille(ligneOriginale);

        DemandeFille ligneStock = new DemandeFille();
        ligneStock.setDemandeMere(ligneOriginale.getDemandeMere());
        ligneStock.setArticle(ligneOriginale.getArticle());
        ligneStock.setQuantite(String.valueOf(qteStock));
        ligneStock.setPrixUnitaire(prixFifo);
        ligneStock.setStatut(ligneOriginale.getStatut());
        ligneStock.setTypeApprovisionnement(DemandeFille.TypeApprovisionnement.STOCK);
        demandeFilleService.saveDemandeFille(ligneStock);

        demandeMereService.recalculerTotal(ligneOriginale.getDemandeMere());
        return ResponseEntity.ok(Map.of("ok", true));
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

        // Récupérer toutes les lignes stock du même article dans la même demande
        DemandeMere demande = ligne.getDemandeMere();
        double totalReserveAutres = demandeFilleService.getDemandeFilleByDemandeMere(demande)
                .stream()
                .filter(l -> l.getId() != ligneId)
                .filter(l -> l.getTypeApprovisionnement() == DemandeFille.TypeApprovisionnement.STOCK)
                .filter(l -> l.getArticle().getCodeArticle().equals(codeArticle))
                .mapToDouble(DemandeFille::getQuantite)
                .sum();

        double stockDisponible = Math.max(0, stockPhysique - totalReserveAutres);

        return ResponseEntity.ok(Map.of(
                "stockDisponible", stockDisponible,
                "stockPhysique", stockPhysique,
                "totalReserveAutres", totalReserveAutres
        ));
    }

}
