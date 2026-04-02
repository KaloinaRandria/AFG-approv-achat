package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.email.EmailSenderService;
import afg.achat.afgApprovAchat.email.Mail;
import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.demande.*;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.util.CommentaireFinance;
import afg.achat.afgApprovAchat.model.util.MontantCalculator;
import afg.achat.afgApprovAchat.model.util.PrixArticle;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.bonSortie.BonSortieMereRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.demande.*;
import afg.achat.afgApprovAchat.service.util.CommentaireFinanceService;
import afg.achat.afgApprovAchat.service.util.PrixArticleService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.upload.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class DemandeController {
    @Autowired
    DemandeMereService demandeMereService;
    @Autowired
    DemandeFilleService demandeFilleService;
    @Autowired
    ArticleService articleService;
    @Autowired
    UtilisateurService utilisateurService;
    @Autowired
    IdGenerator idGenerator;
    @Autowired
    CentreBudgetaireService centreBudgetaireService;
    @Autowired
    StorageService storageService;
    @Autowired
    DemandePieceJointeService demandePieceJointeService;
    @Autowired
    ValidationDemandeService validationDemandeService;
    @Autowired
    CodepPieceJointeService codepPieceJointeService;
    @Autowired
    private CommentaireFinanceService commentaireFinanceService;
    @Autowired
    private EmailSenderService ess;
    @Autowired
    PrixArticleService prixArticleService;
    @Autowired
    BonSortieMereRepo bsMereRepo;

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
            this.demandeMereService.saveDemandeMere(demandeMere);

            // Save les lignes (après demandeMere pour que la FK soit valide)
            for (DemandeFille ligne : lignes) {
                ligne.setDemandeMere(demandeMere); //FK settée après persist
                this.demandeFilleService.saveDemandeFille(ligne);
            }
            if (piecesJointes != null) {
                for (MultipartFile f : piecesJointes) {
                    if (f == null || f.isEmpty()) continue;

                    // Vérification avant stockage
                    String contentType = f.getContentType();
                    if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
                        redirectAttributes.addFlashAttribute("ko",
                                "Fichier refusé : '" + f.getOriginalFilename() + "'. Seuls les images et PDF sont autorisés.");
                        return "redirect:/demande/add";
                    }

                    String safeDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    String ref = demandeMere.getId()
                            + "_" + demandeMere.getDemandeur().getNom()
                            + "_" + demandeMere.getDemandeur().getPrenom()
                            + "_" + safeDate;

                    String storedName = storageService.store(f, ref);

                    DemandePieceJointe pj = new DemandePieceJointe();
                    pj.setDemande(demandeMere);
                    pj.setOriginalName(f.getOriginalFilename());
                    pj.setStoredName(storedName);
                    pj.setContentType(contentType);
                    pj.setSize(f.getSize());
                    pj.setUploadedAt(LocalDateTime.now());

                    demandePieceJointeService.insert(pj);
                }
            }

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
//            ess.sendEmail(mail);

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
//                ess.sendEmail(mailSup);
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
    public String listDemandePage(Model model,
                                  HttpServletRequest request,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(defaultValue = "dateDemande") String sort,
                                  @RequestParam(defaultValue = "desc") String dir,
                                  @RequestParam(required = false) Integer statut,
                                  @RequestParam(required = false) String priorite,
                                  @RequestParam(required = false) String num,
                                  @RequestParam(required = false) String demandeur,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                  @RequestParam(required = false, defaultValue = "ALL") String scope) {

        // ── 1. Contexte utilisateur ──────────────────────────────────────────────
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin      = hasRole(auth, "ROLE_ADMIN");
        boolean isMG         = hasRole(auth, "ROLE_MOYENS_GENERAUX");
        boolean isControleur = hasRole(auth, "ROLE_CONTROLEUR");
        boolean isDFC        = hasRole(auth, "ROLE_DFC");
        boolean isSG         = hasRole(auth, "ROLE_SG");

        boolean isBackofficeValidator = isAdmin || isMG || isControleur || isDFC;

        // ── 2. Visibilité hiérarchique ───────────────────────────────────────────
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        boolean hasChildren = visibleIds.size() > 1;

        // Scope (filtre portée pour les managers non-backoffice)
        List<Integer> idsToUse = resolveScope(scope, current, visibleIds, isAdmin || isMG || isControleur);

        // ── 3. Statuts autorisés selon le rôle ──────────────────────────────────
        Integer statutFilter = (statut == null || statut == 0) ? null : statut;
        List<Integer> statutsAutorises = resolveStatutsAutorises(
                isAdmin, isMG, isControleur, isDFC, isSG, statutFilter
        );

        // ── 4. Requête principale ────────────────────────────────────────────────
        Page<DemandeMere> demandesMeres;

        if (isAdmin) {
            // Admin : voit tout, pagination native
            demandesMeres = demandeMereService.searchDemandes(
                    num, demandeur, type, statutFilter, priorite,
                    dateFrom, dateTo, page, size, sort, dir
            );

        } else if (isMG || isControleur || isDFC || isSG) {
            // Backoffice : statuts autorisés globalement
            // + ses propres demandes (tous statuts) via UNION SQL → 1 requête
            demandesMeres = demandeMereService.searchDemandesBackoffice(
                    num, demandeur, type,
                    statutsAutorises, statutFilter,
                    priorite, dateFrom, dateTo,
                    visibleIds,
                    page, size, sort, dir
            );

        } else {
            // Utilisateur simple : seulement ses demandes visibles
            demandesMeres = demandeMereService.searchDemandesVisibleParUtilisateur(
                    num, demandeur, type, statutFilter, priorite,
                    dateFrom, dateTo,
                    idsToUse.isEmpty() ? List.of(-1) : idsToUse,
                    page, size, sort, dir
            );
        }

        // ── 5. Model ─────────────────────────────────────────────────────────────
        populateModel(model, demandesMeres, statut, priorite, num, demandeur,
                type, dateFrom, dateTo, scope, size, sort, dir,
                isBackofficeValidator, hasChildren,
                isMG, isControleur, isDFC, isSG, isAdmin);

        return "demande/demande-liste";
    }

// ── Helpers privés ───────────────────────────────────────────────────────────

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    private List<Integer> resolveScope(String scope, Utilisateur current,
                                       List<Integer> visibleIds, boolean isPrivileged) {
        if (isPrivileged) return visibleIds;
        if ("ME".equalsIgnoreCase(scope))       return List.of(current.getId());
        if ("CHILDREN".equalsIgnoreCase(scope)) return visibleIds.stream()
                .filter(id -> !id.equals(current.getId())).toList();
        return visibleIds; // ALL
    }

    private List<Integer> resolveStatutsAutorises(boolean isAdmin, boolean isMG,
                                                  boolean isControleur, boolean isDFC,
                                                  boolean isSG, Integer statutFilter) {
        List<Integer> base;

        if (isAdmin) {
            return null; // pas de restriction
        } else if (isMG) {
            base = List.of(
                    StatutDemande.VALIDATION_N1, StatutDemande.VALIDATION_N2,
                    StatutDemande.VALIDATION_N3, StatutDemande.VALIDATION_N4,
                    StatutDemande.DECISION_CODEP, StatutDemande.VALIDE, StatutDemande.REFUSE
            );
        } else if (isControleur) {
            base = List.of(
                    StatutDemande.VALIDATION_N2, StatutDemande.VALIDATION_N3,
                    StatutDemande.VALIDATION_N4, StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDE, StatutDemande.REFUSE
            );
        } else if (isDFC) {
            base = List.of(
                    StatutDemande.VALIDATION_N3, StatutDemande.VALIDATION_N4,
                    StatutDemande.DECISION_CODEP, StatutDemande.VALIDE, StatutDemande.REFUSE
            );
        } else if (isSG) {
            base = List.of(
                    StatutDemande.VALIDATION_N4, StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDE, StatutDemande.REFUSE
            );
        } else {
            return null;
        }

        // Appliquer le filtre statut sélectionné par l'utilisateur
        if (statutFilter != null) {
            return base.contains(statutFilter) ? List.of(statutFilter) : List.of();
        }

        return new ArrayList<>(base);
    }

    private void populateModel(Model model, Page<DemandeMere> demandesMeres,
                               Integer statut, String priorite, String num,
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

        // Colonnes visibilité
        model.addAttribute("showDemandeurColumn",       isBackofficeValidator || hasChildren);
        model.addAttribute("showDemandeurScopeFilter",  hasChildren && !isBackofficeValidator);

        // Flags de vue
        model.addAttribute("isMGOnly",         isMG && !isAdmin);
        model.addAttribute("isControleurOnly", isControleur && !isAdmin);
        model.addAttribute("isDFCOnly",        isDFC && !isAdmin);
        model.addAttribute("isSGOnly",         isSG && !isAdmin);

        // Données pagination / filtres
        model.addAttribute("demandesMeres", demandesMeres);
        model.addAttribute("statut",    (statut == null) ? 0 : statut);
        model.addAttribute("priorite",  priorite == null ? "" : priorite);
        model.addAttribute("num",       num == null ? "" : num);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("type",      type == null ? "" : type);
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
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());
        DemandeFille ligne = demandeFilleService.getDemandeFilleById(id);
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
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        DemandeFille ligne = demandeFilleService.getDemandeFilleById(ligneId);
        if (ligne == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Ligne introuvable"));
        }

        if (quantite <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La quantité doit être supérieure à 0"));
        }

        // Garder l'ancienne quantité pour l'historique
        double ancienneQuantite = ligne.getQuantite();

        // Mettre à jour la quantité
        ligne.setQuantite(String.valueOf(quantite));
        demandeFilleService.saveDemandeFille(ligne);

        // Recalculer le total de la demande mère
        DemandeMere demande = ligne.getDemandeMere();
        List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);
        // APRÈS — utilise prixUnitaire de DemandeFille + met à jour totalEstime
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
                    // Pour la ligne qu'on vient de modifier, utiliser la nouvelle quantité
                    double qte = (l.getId() == ligneId) ? quantite : l.getQuantite();
                    return qte * l.getPrixUnitaire();
                })
                .sum();

        demande.setTotalEstime(newTotalEstime > 0 ? newTotalEstime : null);
        demandeMereService.saveDemandeMere(demande);

        //Historique de la modification
        String designation  = ligne.getArticle() != null ? ligne.getArticle().getDesignation()  : "Article inconnu";
        String codeArticle  = ligne.getArticle() != null ? ligne.getArticle().getCodeArticle()  : "N/A";

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
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        boolean isMG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));

        boolean isControleur = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CONTROLEUR".equals(a.getAuthority()));

        boolean isDFC = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DFC".equals(a.getAuthority()));

        boolean isSG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SG".equals(a.getAuthority()));

        boolean isAdminOrSpecial = isAdmin || isMG || isControleur || isDFC || isSG;


        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable : " + id);
            return "redirect:/demande/list";
        }
        Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;

        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        if (!isAdminOrSpecial) {
            if (demandeurId == null || !visibleIds.contains(demandeurId)) {
                redirectAttributes.addFlashAttribute("ko", "Accès refusé à cette demande.");
                return "redirect:/demande/list";
            }
        }

        // Enfants directs (sans moi)
        List<Integer> childrenIds = visibleIds.stream()
                .filter(x -> !x.equals(current.getId()))
                .toList();

        // N+1 du demandeur = la demande appartient à un de mes enfants
        boolean isViewerNplus1OfDemandeur = (demandeurId != null) && childrenIds.contains(demandeurId);

        // Droits de décision par niveau
        boolean canDecisionN1 = isViewerNplus1OfDemandeur && demande.getStatut() == StatutDemande.CREE;
        boolean canDecisionMG = isMG && demande.getStatut() == StatutDemande.VALIDATION_N1;
        boolean canDecisionControleur = isControleur && demande.getStatut() == StatutDemande.VALIDATION_N2;
        boolean canDecisionDFC = isDFC && demande.getStatut() == StatutDemande.VALIDATION_N3;
        boolean canDecisionSG   = isSG  && demande.getStatut() == StatutDemande.VALIDATION_N4;
        boolean isCodepWorkflow = Boolean.TRUE.equals(demande.getViaCodep());
        boolean canDecisionCodep = isMG && demande.getStatut() == StatutDemande.DECISION_CODEP;

        boolean isValidatedCodep = demande.getStatut() == StatutDemande.VALIDE && demande.getDecisionViaCodep(isCodepWorkflow);

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



        // statutHint (UI) : message adapté au viewer
        String statutHint = null;

        // N+1 a validé (donc la demande est passée à N1)
        if (isViewerNplus1OfDemandeur && demande.getStatut() == StatutDemande.VALIDATION_N1) {
            statutHint = "Vous avez validé — en attente de traitement par les Moyens Généraux.";
        }

        // MG : soit en attente de MG (N1), soit déjà traité par MG (N2)
        if (isMG) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N1) {
                statutHint = "Demande en attente de votre validation (Moyens Généraux).";
            } else if (demande.getStatut() == StatutDemande.VALIDATION_N2) {
                statutHint = "Vous avez validé — en attente de traitement par le contrôleur de gestion.";
            }
        }


        if (isControleur) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N2) {
                statutHint = "Demande en attente de votre validation (contrôleur de gestion).";
            } else if (demande.getStatut() == StatutDemande.VALIDATION_N3) {
                statutHint = "Vous avez validé — en attente de validation finale (D.F.C.).";
            }
        }


        if (isDFC) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N3) {
                statutHint = "Demande en attente de votre validation (D.F.C.).";
            } else if (demande.getStatut() == StatutDemande.VALIDATION_N4) {
                statutHint = "Vous avez validé — en attente de validation finale (S.G.).";
            }
        }


        if (isSG) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N4) {
                statutHint = "Demande en attente de votre validation finale (S.G.).";
            } else if (demande.getStatut() == StatutDemande.VALIDE) {
                statutHint = "Demande finalisée.";
            }
        }

        List<DemandePieceJointe> piecesJointes = demandePieceJointeService.listByDemandeId(demande.getId());
        List<CodepPieceJointe> codepPiecesJointes = codepPieceJointeService.listByDemandeId(demande.getId());
        CommentaireFinance commentaireFinance = commentaireFinanceService.getCommentaireFinanceByIdDemande(demande); // ← AJOUTER


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

        boolean canVoirPrix = isAdminOrSpecial || isViewerNplus1OfDemandeur;
        model.addAttribute("canVoirPrix", canVoirPrix);

        model.addAttribute("steps", steps);
        model.addAttribute("historiques", historiques);
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
        model.addAttribute("ligneBudgetaires", ligneBudgetaires);
        model.addAttribute("commentaireFinance", commentaireFinance);

// ── Bon de Sortie lié à cette demande ────────────────────────────────────
        BonSortieMere bonSortie = bsMereRepo.findFirstByDemandeMereOrderByIdDesc(demande).orElse(null);
        model.addAttribute("bonSortie", bonSortie);

        return "demande/demande-fiche";
    }




    @PostMapping("/fiche/{id}/decision")
    public String decision(@PathVariable("id") String id,
                           @RequestParam("decision") String decision,
                           @RequestParam(value = "typeDemande", required = false) String typeDemande,
                           @RequestParam(value = "commentaire", required = false) String commentaire,
                           @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
                           @RequestParam(name = "ligneBudgetaire", required = false) String ligneBudgetaire,
                           @RequestParam(name = "commentaireControleur" , required = false) String commentaireControleur,
                           RedirectAttributes redirectAttributes, HttpServletRequest request)  {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        boolean isMG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));

        boolean isControleur = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CONTROLEUR".equals(a.getAuthority()));

        boolean isDFC = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DFC".equals(a.getAuthority()));

        boolean isSG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SG".equals(a.getAuthority()));

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        String action = (decision == null) ? "" : decision.trim().toUpperCase();

        Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;

        // Enfants directs du current
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        List<Integer> childrenIds = visibleIds.stream()
                .filter(x -> !x.equals(current.getId()))
                .toList();

        // Autorisations par niveau
        boolean canDecisionN1 = demandeurId != null
                && childrenIds.contains(demandeurId)
                && demande.getStatut() == StatutDemande.CREE;

        boolean canDecisionMG = isMG && demande.getStatut() == StatutDemande.VALIDATION_N1;
        boolean canDecisionControleur = isControleur && demande.getStatut() == StatutDemande.VALIDATION_N2;
        boolean canDecisionDFC = isDFC && demande.getStatut() == StatutDemande.VALIDATION_N3;
        boolean canDecisionSG = isSG && demande.getStatut() == StatutDemande.VALIDATION_N4;
        boolean canDecisionCodep = isMG && demande.getStatut() == StatutDemande.DECISION_CODEP;
        boolean allowed = isAdmin || canDecisionN1 || canDecisionMG || canDecisionControleur
                || canDecisionDFC || canDecisionSG || canDecisionCodep;

        if (!allowed) {
            redirectAttributes.addFlashAttribute("ko", "Cette demande ne peut pas être traitée par vous.");
            return "redirect:/demande/fiche/" + id;
        }

        String cmt = (commentaire == null) ? "" : commentaire.trim();

        // ----- REJECT : commentaire obligatoire -----
        if ("REJECT".equals(action)) {

            if (cmt.isBlank()) {
                redirectAttributes.addFlashAttribute("ko",
                        "Le commentaire est obligatoire pour rejeter une demande.");
                return "redirect:/demande/fiche/" + id;
            }
            sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_REFUS", redirectAttributes);

            //recharger la demande pour avoir le vrai statut DB
            demande = demandeMereService
                    .getDemandeMereById(id)
                    .orElseThrow();

            int etapeCourante = demande.getStatut();

            ValidationDemande h = new ValidationDemande();
            h.setDemandeMere(demande);
            h.setValidateur(current);
            h.setEtape(etapeCourante);
            h.setDecision(ValidationDemande. DecisionValidation.REFUSE);
            h.setCommentaire(cmt);
            h.setDateAction(String.valueOf(LocalDateTime.now()));

            validationDemandeService.logAction(h);

            // ensuite seulement changer statut
            demandeMereService.appliquerDecisionGlobale(
                    demande,
                    StatutDemande.REFUSE
            );

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
//            ess.sendEmail(mail);

            redirectAttributes.addFlashAttribute("ok", "Demande rejetée.");
            return "redirect:/demande/fiche/" + id;
        }


        // ----- APPROVE : transition selon le niveau -----
        if ("APPROVE".equals(action)) {

            if (canDecisionN1) {
                int etape = demande.getStatut();
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_N1", redirectAttributes);
                if (!pjAjoutees.isEmpty()) {
                    String listePj = pjAjoutees.stream()
                            .map(nom -> "• " + nom)
                            .collect(Collectors.joining("\n"));

                    ValidationDemande histoPj = new ValidationDemande();
                    histoPj.setDemandeMere(demande);
                    histoPj.setValidateur(current);
                    histoPj.setEtape(etape);
                    histoPj.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                    histoPj.setCommentaire(pjAjoutees.size() + " pièce(s) jointe(s) ajoutée(s) :\n" + listePj);
                    histoPj.setDateAction(String.valueOf(LocalDateTime.now()));
                    validationDemandeService.logAction(histoPj);
                }
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N1);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                List<Utilisateur> mgs = utilisateurService.getUtilisateursByRole("MOYENS_GENERAUX");
                System.out.println("Nombre de MG trouvés : " + mgs.size());
                for (Utilisateur mg : mgs) {
                    System.out.println("Envoi mail à MG : " + mg.getMail());
//                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N1, mg);
                }

                redirectAttributes.addFlashAttribute("ok", "Demande envoyée en validation N1 (MG).");
                return "redirect:/demande/fiche/" + id;
            }
            if(canDecisionCodep) {
                final List<String> ALLOWED_MIME = List.of(
                        "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"
                );
                List<String> pjAjoutees = new ArrayList<>();

                if (piecesJointes != null) {
                    for (MultipartFile f : piecesJointes) {
                        if (f == null || f.isEmpty()) continue;

                        String contentType = f.getContentType() != null ? f.getContentType() : "";

                        // ← Vérification côté serveur
                        if (!ALLOWED_MIME.contains(contentType)) {
                            redirectAttributes.addFlashAttribute("ko",
                                    "Format non autorisé : " + f.getOriginalFilename() + " (PDF et images uniquement).");
                            return "redirect:/demande/fiche/" + id;
                        }

                        String safeDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                        String ref = "PV_CODEP_" + demande.getId()
                                + "_" + demande.getDemandeur().getNom()
                                + "_" + demande.getDemandeur().getPrenom()
                                + "_" + safeDate;

                        String storedName = storageService.store(f, ref);

                        CodepPieceJointe pj = new CodepPieceJointe();
                        pj.setDemandeMere(demande);
                        pj.setOriginalName(f.getOriginalFilename());
                        pj.setStoredName(storedName);
                        pj.setContentType(contentType);
                        pj.setSize(f.getSize());
                        pj.setUploadedAt(LocalDateTime.now());

                        codepPieceJointeService.insert(pj);
                        pjAjoutees.add(f.getOriginalFilename());
                    }
                }


                int etape = demande.getStatut();
                if (!pjAjoutees.isEmpty()) {
                    String listePj = pjAjoutees.stream()
                            .map(nom -> "• " + nom)
                            .collect(Collectors.joining("\n"));

                    ValidationDemande histoPj = new ValidationDemande();
                    histoPj.setDemandeMere(demande);
                    histoPj.setValidateur(current);
                    histoPj.setEtape(etape);
                    histoPj.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                    histoPj.setCommentaire(pjAjoutees.size() + " pièce(s) jointe(s) CODEP ajoutée(s) :\n" + listePj);
                    histoPj.setDateAction(String.valueOf(LocalDateTime.now()));
                    validationDemandeService.logAction(histoPj);
                }
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                redirectAttributes.addFlashAttribute("ok", "Demande validée par le Comité de dépense.");
                return "redirect:/demande/fiche/" + id;
            }

            if (canDecisionMG) {
                int etape = demande.getStatut();
                List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);
                List<String> lignesSansPrix = lignes.stream()
                        .filter(l -> l.getStatut() != StatutDemande.REFUSE)
                        .filter(l -> l.getPrixUnitaire() == null || l.getPrixUnitaire() <= 0)
                        .map(l -> l.getArticle().getCodeArticle() + " - " + l.getArticle().getDesignation())
                        .toList();

                if (!lignesSansPrix.isEmpty()) {
                    redirectAttributes.addFlashAttribute("ko",
                            "Impossible de valider : les articles suivants n'ont pas de prix unitaire : "
                                    + String.join(", ", lignesSansPrix));
                    return "redirect:/demande/fiche/" + id;
                }
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_MG", redirectAttributes);
                if (!pjAjoutees.isEmpty()) {
                    String listePj = pjAjoutees.stream()
                            .map(nom -> "• " + nom)
                            .collect(Collectors.joining("\n"));

                    ValidationDemande histoPj = new ValidationDemande();
                    histoPj.setDemandeMere(demande);
                    histoPj.setValidateur(current);
                    histoPj.setEtape(etape);
                    histoPj.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                    histoPj.setCommentaire(pjAjoutees.size() + " pièce(s) jointe(s) ajoutée(s) :\n" + listePj);
                    histoPj.setDateAction(String.valueOf(LocalDateTime.now()));
                    validationDemandeService.logAction(histoPj);
                }
                //MG doit obligatoirement choisir le type (OPEX/CAPEX)
                String td = (typeDemande == null) ? "" : typeDemande.trim().toUpperCase();
                if (td.isBlank()) {
                    redirectAttributes.addFlashAttribute("ko", "Veuillez choisir le Type de demande (OPEX/CAPEX) avant de valider.");
                    return "redirect:/demande/fiche/" + id;
                }

                try {
                    demande.setNatureDemande(DemandeMere.NatureDemande.valueOf(td));
                } catch (IllegalArgumentException ex) {
                    redirectAttributes.addFlashAttribute("ko", "Type de demande invalide : " + typeDemande);
                    return "redirect:/demande/fiche/" + id;
                }

                String prioriteParam = request.getParameter("priorite");
                String anciennePriorite = demande.getPriorite() != null
                        ? demande.getPriorite().name() : "N/A";

                if (prioriteParam != null && !prioriteParam.isBlank()) {
                    try {
                        DemandeMere.PrioriteDemande nouvellePriorite =
                                DemandeMere.PrioriteDemande.valueOf(prioriteParam.trim().toUpperCase());

                        // Historique seulement si la priorité a changé
                        if (!prioriteParam.trim().toUpperCase().equals(anciennePriorite)) {
                            demande.setPriorite(nouvellePriorite);

                            ValidationDemande histoPriorite = new ValidationDemande();
                            histoPriorite.setDemandeMere(demande);
                            histoPriorite.setValidateur(current);
                            histoPriorite.setEtape(demande.getStatut());
                            histoPriorite.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                            histoPriorite.setCommentaire(
                                    "Modification de priorité : " + anciennePriorite
                                            + " -> " + nouvellePriorite.name()
                            );
                            histoPriorite.setDateAction(String.valueOf(LocalDateTime.now()));
                            validationDemandeService.logAction(histoPriorite);
                        }

                    } catch (IllegalArgumentException ex) {
                        redirectAttributes.addFlashAttribute("ko",
                                "Priorité invalide : " + prioriteParam);
                        return "redirect:/demande/fiche/" + id;
                    }
                }


                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
                validationDemandeService.logValidation(demande, current, cmt , etape);
                List<Utilisateur> controleurs = utilisateurService.getUtilisateursByRole("CONTROLEUR");
//                for (Utilisateur controleur : controleurs) {
//                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N2, controleur);
//                }
                redirectAttributes.addFlashAttribute("ok", "Demande validée par les Moyens Généraux (N2).");
                return "redirect:/demande/fiche/" + id;
            }

            if (canDecisionControleur) {
                int etape = demande.getStatut();

                try {
                    List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_CONTROLEUR", redirectAttributes);
                    if (!pjAjoutees.isEmpty()) {
                        String listePj = pjAjoutees.stream()
                                .map(nom -> "• " + nom)
                                .collect(Collectors.joining("\n"));

                        ValidationDemande histoPj = new ValidationDemande();
                        histoPj.setDemandeMere(demande);
                        histoPj.setValidateur(current);
                        histoPj.setEtape(etape);
                        histoPj.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                        histoPj.setCommentaire(pjAjoutees.size() + " pièce(s) jointe(s) ajoutée(s) :\n" + listePj);
                        histoPj.setDateAction(String.valueOf(LocalDateTime.now()));
                        validationDemandeService.logAction(histoPj);
                    }
                    demande.setCentreBudgetaire(centreBudgetaireService.getCentreBudgetaireById(Integer.parseInt(ligneBudgetaire)));

                    CommentaireFinance commentaireFinance = new CommentaireFinance();
                    commentaireFinance.setCommentateur(current);
                    commentaireFinance.setDemandeMere(demande);
                    commentaireFinance.setCommentaire(commentaireControleur);

                    commentaireFinanceService.insertCommentaireFinance(commentaireFinance);
                } catch (IllegalArgumentException ex) {
                    if (ligneBudgetaire.isEmpty() || ligneBudgetaire.isBlank()) {
                        redirectAttributes.addFlashAttribute("ko", "ligne budgetaire obligatoire pour le contrôleur de gestion.");
                    }
                    if (commentaireControleur.isEmpty() || commentaireControleur.isBlank()) {
                        redirectAttributes.addFlashAttribute("ko", "Le commentaire du contrôleur de gestion est obligatoire.");
                    }
                    return "redirect:/demande/fiche/" + id;
                }
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N3);
                validationDemandeService.logValidation(demande, current, cmt , etape);
                List<Utilisateur> dfcs = utilisateurService.getUtilisateursByRole("DFC");
//                for (Utilisateur dfc : dfcs) {
//                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N3, dfc);
//                }
                redirectAttributes.addFlashAttribute("ok", "Demande validée par le contrôleur de gestion (N3).");
                return "redirect:/demande/fiche/" + id;
            }

            if (canDecisionDFC) {
                int etape = demande.getStatut();
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_DFC", redirectAttributes);
                if (!pjAjoutees.isEmpty()) {
                    String listePj = pjAjoutees.stream()
                            .map(nom -> "• " + nom)
                            .collect(Collectors.joining("\n"));

                    ValidationDemande histoPj = new ValidationDemande();
                    histoPj.setDemandeMere(demande);
                    histoPj.setValidateur(current);
                    histoPj.setEtape(etape);
                    histoPj.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                    histoPj.setCommentaire(pjAjoutees.size() + " pièce(s) jointe(s) ajoutée(s) :\n" + listePj);
                    histoPj.setDateAction(String.valueOf(LocalDateTime.now()));
                    validationDemandeService.logAction(histoPj);
                }
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N4);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                List<Utilisateur> sgs = utilisateurService.getUtilisateursByRole("SG");
//                for (Utilisateur sg : sgs) {
//                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N4, sg);
//                }
                redirectAttributes.addFlashAttribute("ok", "Demande validée par la D.F.C., transmise au S.G.");
                return "redirect:/demande/fiche/" + id;
            }

            if (canDecisionSG) {
                int etape = demande.getStatut();
                List<String> pjAjoutees = sauvegarderPiecesJointesDecision(piecesJointes, demande, "PJ_SG", redirectAttributes);
                if (!pjAjoutees.isEmpty()) {
                    String listePj = pjAjoutees.stream()
                            .map(nom -> "• " + nom).collect(Collectors.joining("\n"));
                    ValidationDemande histoPj = new ValidationDemande();
                    histoPj.setDemandeMere(demande);
                    histoPj.setValidateur(current);
                    histoPj.setEtape(etape);
                    histoPj.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                    histoPj.setCommentaire(pjAjoutees.size() + " pièce(s) jointe(s) ajoutée(s) :\n" + listePj);
                    histoPj.setDateAction(String.valueOf(LocalDateTime.now()));
                    validationDemandeService.logAction(histoPj);
                }
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDE);
                validationDemandeService.logValidation(demande, current, cmt, etape);
//                ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDE, null);
                redirectAttributes.addFlashAttribute("ok", "Demande validée et finalisée par le S.G.");
                return "redirect:/demande/fiche/" + id;
            }

            // Admin : si tu veux le laisser forcer la suite même s'il n'est pas le bon rôle
            if (isAdmin) {
                int next = switch (demande.getStatut()) {
                    case StatutDemande.CREE          -> StatutDemande.VALIDATION_N1;
                    case StatutDemande.VALIDATION_N1 -> StatutDemande.VALIDATION_N2;
                    case StatutDemande.VALIDATION_N2 -> StatutDemande.VALIDATION_N3;
                    case StatutDemande.VALIDATION_N3 -> StatutDemande.VALIDATION_N4;
                    case StatutDemande.VALIDATION_N4 -> StatutDemande.VALIDE;
                    default -> demande.getStatut();
                };
                int etape = demande.getStatut();
                demandeMereService.appliquerDecisionGlobale(demande, next);
                validationDemandeService.logValidation(demande, current, cmt, etape);
                redirectAttributes.addFlashAttribute("ok", "Statut mis à jour (ADMIN).");
                return "redirect:/demande/fiche/" + id;
            }
        }
        System.out.println(">>> decision reçue = [" + decision + "]");
        redirectAttributes.addFlashAttribute("ko", "Décision invalide.");
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

        //MG doit obligatoirement choisir le type (OPEX/CAPEX)
        String td = (typeDemande == null) ? "" : typeDemande.trim().toUpperCase();
        if (td.isBlank()) {
            redirectAttributes.addFlashAttribute("ko", "Veuillez choisir le Type de demande (OPEX/CAPEX) avant de valider.");
            return "redirect:/demande/fiche/" + id;
        }

        try {
            demande.setNatureDemande(DemandeMere.NatureDemande.valueOf(td));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("ko", "Type de demande invalide : " + typeDemande);
            return "redirect:/demande/fiche/" + id;
        }

        if (prioriteParam != null && !prioriteParam.isBlank()) {
            String anciennePriorite = demande.getPriorite() != null
                    ? demande.getPriorite().name() : "N/A";
            try {
                DemandeMere.PrioriteDemande nouvellePriorite =
                        DemandeMere.PrioriteDemande.valueOf(prioriteParam.trim().toUpperCase());

                if (!prioriteParam.trim().toUpperCase().equals(anciennePriorite)) {
                    demande.setPriorite(nouvellePriorite);

                    ValidationDemande histoPriorite = new ValidationDemande();
                    histoPriorite.setDemandeMere(demande);
                    histoPriorite.setValidateur(current);
                    histoPriorite.setEtape(demande.getStatut());
                    histoPriorite.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
                    histoPriorite.setCommentaire("Modification de priorité : " + anciennePriorite
                            + " → " + nouvellePriorite.name());
                    histoPriorite.setDateAction(String.valueOf(LocalDateTime.now()));
                    validationDemandeService.logAction(histoPriorite);
                }
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("ko", "Priorité invalide : " + prioriteParam);
                return "redirect:/demande/fiche/" + id;
            }
        }

        demande.setViaCodep(true);
        List<String> pjAjoutees = new ArrayList<>();
        if (piecesJointes != null) {
            for (MultipartFile f : piecesJointes) {
                if (f == null || f.isEmpty()) continue;

                String safeDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String ref = "PJ_CODEP_" + demande.getId()
                        + "_" + demande.getDemandeur().getNom()
                        + "_" + demande.getDemandeur().getPrenom()
                        + "_" + safeDate;

                String storedName = storageService.store(f, ref);

                CodepPieceJointe pj = new CodepPieceJointe();
                pj.setDemandeMere(demande);
                pj.setOriginalName(f.getOriginalFilename());
                pj.setStoredName(storedName);
                pj.setContentType(f.getContentType() != null ? f.getContentType() : "application/octet-stream");
                pj.setSize(f.getSize());
                pj.setUploadedAt(LocalDateTime.now());

                codepPieceJointeService.insert(pj);
                pjAjoutees.add(f.getOriginalFilename());
            }
        }
        // Passage au statut CODEP
        int etape = demande.getStatut();
        if (!pjAjoutees.isEmpty()) {
            String listePj = pjAjoutees.stream()
                    .map(nom -> "• " + nom)
                    .collect(Collectors.joining("\n"));

            ValidationDemande histoPj = new ValidationDemande();
            histoPj.setDemandeMere(demande);
            histoPj.setValidateur(current);
            histoPj.setEtape(etape);
            histoPj.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
            histoPj.setCommentaire(pjAjoutees.size() + " pièce(s) jointe(s) ajoutée(s) :\n" + listePj);
            histoPj.setDateAction(String.valueOf(LocalDateTime.now()));
            validationDemandeService.logAction(histoPj);
        }
        demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.DECISION_CODEP);
        validationDemandeService.logValidation(demande, current, "Envoi au CODEP", etape);
        redirectAttributes.addFlashAttribute("ok", "Demande envoyée au CODEP (action irréversible).");

        return "redirect:/demande/fiche/" + id;
    }

    // Aperçu — inline (images + PDF)
    @GetMapping("/files/{id}/{filename:.+}")
    public ResponseEntity<Resource> previewDemandeFile(@PathVariable String id,
                                                       @PathVariable String filename) throws IOException {
        Resource file = storageService.loadAsResource(filename);

        String contentType = Files.probeContentType(file.getFile().toPath());
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(file);
    }

    // Téléchargement — attachment forcé
    @GetMapping("/files/{id}/{filename:.+}/download")
    public ResponseEntity<Resource> downloadDemandeFile(@PathVariable String id,
                                                        @PathVariable String filename) throws IOException {
        Resource file = storageService.loadAsResource(filename);

        String contentType = Files.probeContentType(file.getFile().toPath());
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(file);
    }

    private List<String> sauvegarderPiecesJointesDecision(MultipartFile[] piecesJointes,
                                                          DemandeMere demande,
                                                          String prefixRef,
                                                          RedirectAttributes redirectAttributes) {
        if (piecesJointes == null) return List.of();

        List<String> nomsAjoutes = new ArrayList<>();

        for (MultipartFile f : piecesJointes) {
            if (f == null || f.isEmpty()) continue;

            String contentType = f.getContentType();
            if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
                redirectAttributes.addFlashAttribute("ko",
                        "Fichier refusé : '" + f.getOriginalFilename() + "'. Seuls les images et PDF sont autorisés.");
                return nomsAjoutes;
            }

            String safeDate = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String ref = prefixRef
                    + "_" + demande.getId()
                    + "_" + demande.getDemandeur().getNom()
                    + "_" + demande.getDemandeur().getPrenom()
                    + "_" + safeDate;

            String storedName = storageService.store(f, ref);

            DemandePieceJointe pj = new DemandePieceJointe();
            pj.setDemande(demande);
            pj.setOriginalName(f.getOriginalFilename());
            pj.setStoredName(storedName);
            pj.setContentType(contentType);
            pj.setSize(f.getSize());
            pj.setUploadedAt(LocalDateTime.now());

            demandePieceJointeService.insert(pj);
            nomsAjoutes.add(f.getOriginalFilename()); // ← collecte le nom original
        }
        return nomsAjoutes;
    }


}
