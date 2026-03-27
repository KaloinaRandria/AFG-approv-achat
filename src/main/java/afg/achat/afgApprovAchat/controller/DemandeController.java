package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.email.EmailSenderService;
import afg.achat.afgApprovAchat.email.Mail;
import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.demande.*;
import afg.achat.afgApprovAchat.model.util.CommentaireFinance;
import afg.achat.afgApprovAchat.model.util.MontantCalculator;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.demande.*;
import afg.achat.afgApprovAchat.service.util.CommentaireFinanceService;
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

    @GetMapping("/add")
    public String addDemandePage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
        model.addAttribute("ligneBudgetaires",centreBudgetaireService.getAllCentreBudgetaires() );

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
                                RedirectAttributes redirectAttributes) {

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

            for (int i = 0; i < articleCodes.size(); i++) {

                String code = articleCodes.get(i);

                Article article = articleService.getArticleByCodeArticle(code)
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable : " + code));

                double qte = MontantCalculator.parseDoubleSafe(quantite.get(i));
                if (qte <= 0) continue; // ou throw si tu veux obligatoire

                double prix = (article.getPrixUnitaire() == null) ? 0.0 : article.getPrixUnitaire();

                totalGeneral += (qte * prix);
            }

            // (ton contrôle quantité > 0 tu peux le garder ici avant save)

            DemandeMere demandeMere = new DemandeMere();
            demandeMere.setId(idGenerator);
            demandeMere.setDateDemande(String.valueOf(LocalDateTime.now()));
            demandeMere.setDateSortie(dateSortie + "T00:00");
            demandeMere.setPriorite(DemandeMere.PrioriteDemande.valueOf(priorite.trim()));
            demandeMere.setMotifEvoque(motif);
            demandeMere.setDemandeur(utilisateur);
            demandeMere.setDescription(description);
            demandeMere.setStatut(1);
            demandeMere.setTotalPrix(totalGeneral);

            // 1) Save la demande (pour avoir la ref)
            this.demandeMereService.saveDemandeMere(demandeMere);

            // 2) Save les lignes
            for (int i = 0; i < articleCodes.size(); i++) {
                DemandeFille demandeFille = new DemandeFille();
                demandeFille.setDemandeMere(demandeMere);

                String code = articleCodes.get(i);
                demandeFille.setArticle(articleService.getArticleByCodeArticle(code)
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable : " + code)));

                demandeFille.setQuantite(quantite.get(i));
                demandeFille.setStatut(1);
                this.demandeFilleService.saveDemandeFille(demandeFille);
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

            Mail mail = new Mail(
                    "demandeSaved",
                    demandeMere.getDemandeur().getMail(),
                    "[AFG/MADA] - Demande enregistrée",
                    propsDemandeur
            );
            ess.sendEmail(mail);

// ── Mail 2 : notification au N+1 pour validation ────────────────────────
            Utilisateur superieur = demandeMere.getDemandeur().getSuperieurHierarchique();

            if (superieur != null && superieur.getMail() != null) {
                Map<String, Object> propsSup = new HashMap<>();
                propsSup.put("id",           demandeMere.getId());
                propsSup.put("demandeur",    demandeMere.getDemandeur());
                propsSup.put("destinataire", superieur);                  // ← le N+1
                propsSup.put("validateur",   demandeMere.getDemandeur()); // ← le créateur = "validateur" initial
                propsSup.put("etape",        StatutDemande.getLibelle(StatutDemande.CREE));
                propsSup.put("dateDecision", LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

                Mail mailSup = new Mail(
                        "validSup",
                        superieur.getMail(),
                        "[AFG/MADA] - Demande d'achat en attente de votre validation",
                        propsSup
                );
                ess.sendEmail(mailSup);
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

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isMG = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));
        boolean isControleur = auth.getAuthorities().stream().anyMatch(a -> "ROLE_CONTROLEUR".equals(a.getAuthority()));
        boolean isDFC = auth.getAuthorities().stream().anyMatch(a -> "ROLE_DFC".equals(a.getAuthority()));
        boolean isSG = auth.getAuthorities().stream().anyMatch(a -> "ROLE_SG".equals(a.getAuthority()));


        boolean isAdminOrMGOrControleur = isAdmin || isMG || isControleur;
        boolean isBackofficeValidator = isAdmin || isMG || isControleur || isDFC;

        //Labels (table)
        Map<Integer, String> statutLabels = Map.of(
                StatutDemande.CREE, "En attente N+1",
                StatutDemande.VALIDATION_N1, "En attente M.G.",
                StatutDemande.VALIDATION_N2, "En attente Contrôle de gestion",
                StatutDemande.VALIDATION_N3, "En attente D.F.C.",
                StatutDemande.VALIDATION_N4,  "En attente S.G.",
                StatutDemande.DECISION_CODEP,"En attente CODEP",
                StatutDemande.VALIDE, "VALIDÉE",
                StatutDemande.REFUSE, "REFUSÉE"
        );

        //Filtre (select)
        Map<Integer, String> statutFiltre = new LinkedHashMap<>();
        statutFiltre.put(StatutDemande.CREE, "En attente N+1");
        statutFiltre.put(StatutDemande.VALIDATION_N1, "En attente M.G.");
        statutFiltre.put(StatutDemande.VALIDATION_N2,"En attente Contrôle de gestion");
        statutFiltre.put(StatutDemande.VALIDATION_N3, "En attente D.F.C.");
        statutFiltre.put(StatutDemande.VALIDATION_N4,  "En attente S.G.");
        statutFiltre.put(StatutDemande.DECISION_CODEP,"En attente CODEP");
        statutFiltre.put(StatutDemande.VALIDE, "VALIDÉE");
        statutFiltre.put(StatutDemande.REFUSE, "REFUSÉE");
        model.addAttribute("statutFiltre", statutFiltre);

        //Normalisation (0/null => pas de filtre)
        Integer statutFilter = (statut == null || statut == 0) ? null : statut;

        //Visibilité (moi + enfants)
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        boolean hasChildren = visibleIds.size() > 1;

        boolean showDemandeurColumn = isBackofficeValidator || hasChildren;
        model.addAttribute("showDemandeurColumn", showDemandeurColumn);

        boolean showScopeFilter = hasChildren && !isBackofficeValidator;
        model.addAttribute("showDemandeurScopeFilter", showScopeFilter);

        List<Integer> idsToUse = visibleIds;
        if (!isAdminOrMGOrControleur) {
            if ("ME".equalsIgnoreCase(scope)) {
                idsToUse = List.of(current.getId());
            } else if ("CHILDREN".equalsIgnoreCase(scope)) {
                idsToUse = visibleIds.stream()
                        .filter(idU -> !idU.equals(current.getId()))
                        .toList();
            }
        }

        Page<DemandeMere> demandesMeres;

// Cas MG : voit tout ce qui est déjà validé par N+1 (N1 -> VALIDE)
        if (isMG && !isAdmin) {

            List<Integer> mgStatuses = new ArrayList<>(List.of(
                    StatutDemande.VALIDATION_N1,
                    StatutDemande.VALIDATION_N2,
                    StatutDemande.VALIDATION_N3,
                    StatutDemande.VALIDATION_N4,
                    StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDE,
                    StatutDemande.REFUSE
            ));

            // appliquer le filtre statut AVANT la boucle
            if (statutFilter != null) {
                mgStatuses = mgStatuses.contains(statutFilter) ? List.of(statutFilter) : List.of();
            }

            List<DemandeMere> merged = new ArrayList<>();

            for (Integer st : mgStatuses) {
                Page<DemandeMere> p = demandeMereService.searchDemandes(
                        num, demandeur, type,
                        st,
                        priorite,
                        dateFrom, dateTo,
                        0, Integer.MAX_VALUE,
                        sort, dir
                );
                merged.addAll(p.getContent());
            }

            merged.sort(Comparator.comparing(DemandeMere::getDateDemande).reversed());

            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), merged.size());

            List<DemandeMere> slice = (start >= merged.size()) ? List.of() : merged.subList(start, end);
            demandesMeres = new PageImpl<>(slice, pageable, merged.size());

            // pour que la valeur sélectionnée reste affichée dans le select
            model.addAttribute("statut", (statut == null) ? 0 : statut);
        }


        else if (isControleur && !isAdmin) {

            List<Integer> controleurStatuses = new ArrayList<>(List.of(
                    StatutDemande.VALIDATION_N2,
                    StatutDemande.VALIDATION_N3,
                    StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDATION_N4,
                    StatutDemande.VALIDE,
                    StatutDemande.REFUSE
            ));

            if (statutFilter != null) {
                controleurStatuses = controleurStatuses.contains(statutFilter) ? List.of(statutFilter) : List.of();
            }

            List<DemandeMere> merged = new ArrayList<>();

            for (Integer st : controleurStatuses) {
                Page<DemandeMere> p = demandeMereService.searchDemandes(
                        num, demandeur, type,
                        st,
                        priorite,
                        dateFrom, dateTo,
                        0, Integer.MAX_VALUE,
                        sort, dir
                );
                merged.addAll(p.getContent());
            }

            merged.sort(Comparator.comparing(DemandeMere::getDateDemande).reversed());

            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), merged.size());

            List<DemandeMere> slice = (start >= merged.size()) ? List.of() : merged.subList(start, end);
            demandesMeres = new PageImpl<>(slice, pageable, merged.size());

            model.addAttribute("statut", (statut == null) ? 0 : statut);
        }

// Cas SG : voit uniquement N3 (En attente S.G.)
        else if (isDFC && !isAdmin) {

            List<Integer> dfcStatuses = new ArrayList<>(List.of(
                    StatutDemande.VALIDATION_N3,
                    StatutDemande.VALIDATION_N4,
                    StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDE,
                    StatutDemande.REFUSE
            ));

            if (statutFilter != null) {
                dfcStatuses = dfcStatuses.contains(statutFilter) ? List.of(statutFilter) : List.of();
            }

            List<DemandeMere> merged = new ArrayList<>();

            for (Integer st : dfcStatuses) {
                Page<DemandeMere> p = demandeMereService.searchDemandes(
                        num, demandeur, type,
                        st,
                        priorite,
                        dateFrom, dateTo,
                        0, Integer.MAX_VALUE,
                        sort, dir
                );
                merged.addAll(p.getContent());
            }

            merged.sort(Comparator.comparing(DemandeMere::getDateDemande).reversed());

            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), merged.size());

            List<DemandeMere> slice = (start >= merged.size()) ? List.of() : merged.subList(start, end);
            demandesMeres = new PageImpl<>(slice, pageable, merged.size());

            model.addAttribute("statut", (statut == null) ? 0 : statut);
        }

        else if (isSG && !isAdmin) {

            List<Integer> sgStatuses = new ArrayList<>(List.of(
                    StatutDemande.VALIDATION_N4,
                    StatutDemande.DECISION_CODEP,
                    StatutDemande.VALIDE,
                    StatutDemande.REFUSE
            ));

            if (statutFilter != null) {
                sgStatuses = sgStatuses.contains(statutFilter) ? List.of(statutFilter) : List.of();
            }

            List<DemandeMere> merged = new ArrayList<>();
            for (Integer st : sgStatuses) {
                Page<DemandeMere> p = demandeMereService.searchDemandes(
                        num, demandeur, type, st, priorite,
                        dateFrom, dateTo, 0, Integer.MAX_VALUE, sort, dir
                );
                merged.addAll(p.getContent());
            }

            merged.sort(Comparator.comparing(DemandeMere::getDateDemande).reversed());

            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), merged.size());
            List<DemandeMere> slice = (start >= merged.size()) ? List.of() : merged.subList(start, end);
            demandesMeres = new PageImpl<>(slice, pageable, merged.size());

            model.addAttribute("statut", (statut == null) ? 0 : statut);
        }

// Cas normal / admin
        else {
            if (isBackofficeValidator) {
                demandesMeres = demandeMereService.searchDemandes(
                        num, demandeur, type,
                        statutFilter,
                        priorite,
                        dateFrom, dateTo,
                        page, size,
                        sort, dir
                );
            } else {
                demandesMeres = idsToUse.isEmpty()
                        ? demandeMereService.searchDemandesVisibleParUtilisateur(
                        num, demandeur, type, statutFilter, priorite,
                        dateFrom, dateTo,
                        List.of(-1),
                        page, size,
                        sort, dir
                )
                        : demandeMereService.searchDemandesVisibleParUtilisateur(
                        num, demandeur, type, statutFilter, priorite,
                        dateFrom, dateTo,
                        idsToUse,
                        page, size,
                        sort, dir
                );
            }
            model.addAttribute("statut", (statut == null) ? 0 : statut);
        }

        // À ajouter dans listDemandePage, avant le return
        Map<Integer, String> badgeClasses = new HashMap<>();
        badgeClasses.put(StatutDemande.CREE,           "badge-wait");
        badgeClasses.put(StatutDemande.VALIDATION_N1,  "badge-info");
        badgeClasses.put(StatutDemande.VALIDATION_N2,  "badge-purple");
        badgeClasses.put(StatutDemande.VALIDATION_N3,  "badge-warning");
        badgeClasses.put(StatutDemande.VALIDATION_N4,  "badge-teal");
        badgeClasses.put(StatutDemande.DECISION_CODEP, "badge-codep");
        badgeClasses.put(StatutDemande.VALIDE,         "badge-success");
        badgeClasses.put(StatutDemande.REFUSE,         "badge-danger");

        Map<Integer, String> badgeIcons = new HashMap<>();
        badgeIcons.put(StatutDemande.CREE,           "fa-user-clock");
        badgeIcons.put(StatutDemande.VALIDATION_N1,  "fa-clipboard-check");
        badgeIcons.put(StatutDemande.VALIDATION_N2,  "fa-coins");
        badgeIcons.put(StatutDemande.VALIDATION_N3,  "fa-gavel");
        badgeIcons.put(StatutDemande.VALIDATION_N4,  "fa-stamp");
        badgeIcons.put(StatutDemande.DECISION_CODEP, "fa-landmark");
        badgeIcons.put(StatutDemande.VALIDE,         "fa-check-circle");
        badgeIcons.put(StatutDemande.REFUSE,         "fa-times-circle");

        model.addAttribute("badgeClasses", badgeClasses);
        model.addAttribute("badgeIcons", badgeIcons);
        Map<String, String> prioriteFiltre = new LinkedHashMap<>();
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P2), "P2");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P1), "P1");
        prioriteFiltre.put(String.valueOf(DemandeMere.PrioriteDemande.P0), "P0");
        model.addAttribute("prioriteFiltre", prioriteFiltre);

        // Model commun
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("demandesMeres", demandesMeres);

        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("priorite", priorite == null ? "" : priorite);

        model.addAttribute("num", num == null ? "" : num);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("scope", scope);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());
        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
        model.addAttribute("statutLabels", statutLabels);

        // flags de vue
        model.addAttribute("isMGOnly", isMG && !isAdmin);
        model.addAttribute("isControleurOnly", isControleur && !isAdmin);
        model.addAttribute("isDFCOnly", isDFC && !isAdmin);
        model.addAttribute("isSGOnly", isSG && !isAdmin);

        return "demande/demande-liste";
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
        double newTotal = lignes.stream()
                .mapToDouble(l -> {
                    double qte = 0;
                    try { qte = l.getQuantite(); } catch (Exception ignored) {}
                    double prix = (l.getArticle().getPrixUnitaire() == null) ? 0 : l.getArticle().getPrixUnitaire();
                    return qte * prix;
                })
                .sum();

        demande.setTotalPrix(newTotal);
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
                "newTotal", newTotal
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
        model.addAttribute("currentUri", request.getRequestURI());
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
            ess.sendEmail(mail);

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
                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N1, mg);
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
                                            + " → " + nouvellePriorite.name()
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
                for (Utilisateur controleur : controleurs) {
                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N2, controleur);
                }
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
                for (Utilisateur dfc : dfcs) {
                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N3, dfc);
                }
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
                for (Utilisateur sg : sgs) {
                    ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDATION_N4, sg);
                }
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
                ess.envoyerMailValidation(demande, current, cmt, etape, StatutDemande.VALIDE, null);
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
