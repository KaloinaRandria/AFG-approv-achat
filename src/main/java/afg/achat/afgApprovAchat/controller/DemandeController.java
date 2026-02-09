package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandePieceJointe;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.demande.DemandePieceJointeService;
import afg.achat.afgApprovAchat.service.demande.ValidationDemandeService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
                                @RequestParam(name = "piecesJointes", required = false) MultipartFile[] piecesJointes,
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

            // (ton contrôle quantité > 0 tu peux le garder ici avant save)

            DemandeMere demandeMere = new DemandeMere();
            demandeMere.setId(idGenerator);
            demandeMere.setDateDemande(String.valueOf(LocalDateTime.now()));
            demandeMere.setDateSortie(dateSortie);
            demandeMere.setPriorite(DemandeMere.PrioriteDemande.valueOf(priorite.trim()));
            demandeMere.setMotifEvoque(motif);
            demandeMere.setDemandeur(utilisateur);
            demandeMere.setDescription(description);
            demandeMere.setStatut(1);

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

            // 3) Stocker les pièces jointes sur disque + enregistrer en DB
            if (piecesJointes != null) {
                for (MultipartFile f : piecesJointes) {
                    if (f == null || f.isEmpty()) continue;

                    String safeDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    String ref = demandeMere.getId()
                            + "_" + demandeMere.getDemandeur().getNom()
                            + "_" + demandeMere.getDemandeur().getPrenom()
                            + "_" + safeDate;

                    // 1) sauvegarde disque (retourne le nom stocké)
                    String storedName = storageService.store(f, ref);

                    // 2) sauvegarde DB
                    DemandePieceJointe pj = new DemandePieceJointe();
                    pj.setDemande(demandeMere);
                    pj.setOriginalName(f.getOriginalFilename());
                    pj.setStoredName(storedName);
                    pj.setContentType(f.getContentType() != null ? f.getContentType() : "application/octet-stream");
                    pj.setSize(f.getSize());
                    pj.setUploadedAt(LocalDateTime.now());

                    demandePieceJointeService.insert(pj);
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
    public String listDemandePage(Model model,
                                  HttpServletRequest request,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(defaultValue = "dateDemande") String sort,
                                  @RequestParam(defaultValue = "desc") String dir,
                                  @RequestParam(required = false) Integer statut,
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
        boolean isFinance = auth.getAuthorities().stream().anyMatch(a -> "ROLE_FINANCE".equals(a.getAuthority()));
        boolean isSG = auth.getAuthorities().stream().anyMatch(a -> "ROLE_SG".equals(a.getAuthority()));


        boolean isAdminOrMGOrFinance = isAdmin || isMG || isFinance;
        boolean isBackofficeValidator = isAdmin || isMG || isFinance || isSG;

        // ✅ Labels (table)
        Map<Integer, String> statutLabels = Map.of(
                StatutDemande.CREE, "En attente N+1",
                StatutDemande.VALIDATION_N1, "En attente M.G.",
                StatutDemande.VALIDATION_N2, "En attente Finance",
                StatutDemande.VALIDATION_N3, "En attente S.G.",
                StatutDemande.VALIDE, "VALIDÉE",
                StatutDemande.REFUSE, "REFUSÉE"
        );

        // ✅ Filtre (select)
        Map<Integer, String> statutFiltre = new LinkedHashMap<>();
        statutFiltre.put(StatutDemande.CREE, "En attente N+1");
        statutFiltre.put(StatutDemande.VALIDATION_N1, "En attente M.G.");
        statutFiltre.put(StatutDemande.VALIDATION_N2, "En attente Finance");
        statutFiltre.put(StatutDemande.VALIDATION_N3, "En attente S.G.");
        statutFiltre.put(StatutDemande.VALIDE, "VALIDÉE");
        statutFiltre.put(StatutDemande.REFUSE, "REFUSÉE");
        model.addAttribute("statutFiltre", statutFiltre);

        // ✅ Normalisation (0/null => pas de filtre)
        Integer statutFilter = (statut == null || statut == 0) ? null : statut;

        // ✅ Visibilité (moi + enfants)
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        boolean hasChildren = visibleIds.size() > 1;

        boolean showDemandeurColumn = isBackofficeValidator || hasChildren;
        model.addAttribute("showDemandeurColumn", showDemandeurColumn);

        boolean showScopeFilter = hasChildren && !isBackofficeValidator;
        model.addAttribute("showDemandeurScopeFilter", showScopeFilter);

        // ✅ Scope (uniquement non admin/MG/Finance)
        List<Integer> idsToUse = visibleIds;
        if (!isAdminOrMGOrFinance) {
            if ("ME".equalsIgnoreCase(scope)) {
                idsToUse = List.of(current.getId());
            } else if ("CHILDREN".equalsIgnoreCase(scope)) {
                idsToUse = visibleIds.stream()
                        .filter(idU -> !idU.equals(current.getId()))
                        .toList();
            }
        }

        Page<DemandeMere> demandesMeres;

// ✅ Cas MG : voit N1 + N2
        if (isMG && !isAdmin) {

            Page<DemandeMere> p1 = demandeMereService.searchDemandes(
                    num, demandeur, type,
                    StatutDemande.VALIDATION_N1,
                    dateFrom, dateTo,
                    0, Integer.MAX_VALUE,
                    sort, dir
            );

            Page<DemandeMere> p2 = demandeMereService.searchDemandes(
                    num, demandeur, type,
                    StatutDemande.VALIDATION_N2,
                    dateFrom, dateTo,
                    0, Integer.MAX_VALUE,
                    sort, dir
            );

            List<DemandeMere> merged = new ArrayList<>();
            merged.addAll(p1.getContent());
            merged.addAll(p2.getContent());

            merged.sort(Comparator.comparing(DemandeMere::getDateDemande).reversed());

            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), merged.size());

            List<DemandeMere> slice = (start >= merged.size()) ? List.of() : merged.subList(start, end);
            demandesMeres = new PageImpl<>(slice, pageable, merged.size());

            model.addAttribute("statut", 0);
        }
// ✅ Cas FINANCE : voit N2 + N3
        else if (isFinance && !isAdmin) {

            Page<DemandeMere> p2 = demandeMereService.searchDemandes(
                    num, demandeur, type,
                    StatutDemande.VALIDATION_N2,
                    dateFrom, dateTo,
                    0, Integer.MAX_VALUE,
                    sort, dir
            );

            Page<DemandeMere> p3 = demandeMereService.searchDemandes(
                    num, demandeur, type,
                    StatutDemande.VALIDATION_N3,
                    dateFrom, dateTo,
                    0, Integer.MAX_VALUE,
                    sort, dir
            );

            List<DemandeMere> merged = new ArrayList<>();
            merged.addAll(p2.getContent());
            merged.addAll(p3.getContent());

            merged.sort(Comparator.comparing(DemandeMere::getDateDemande).reversed());

            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), merged.size());

            List<DemandeMere> slice = (start >= merged.size()) ? List.of() : merged.subList(start, end);
            demandesMeres = new PageImpl<>(slice, pageable, merged.size());

            model.addAttribute("statut", 0);
        }
// ✅ Cas SG : voit uniquement N3 (En attente S.G.)
        else if (isSG && !isAdmin) {

            demandesMeres = demandeMereService.searchDemandes(
                    num, demandeur, type,
                    StatutDemande.VALIDATION_N3,
                    dateFrom, dateTo,
                    page, size,     // ✅ pas besoin de merge ici
                    sort, dir
            );

            model.addAttribute("statut", 0);
        }
// ✅ Cas normal / admin
        else {
            if (isBackofficeValidator) {
                demandesMeres = demandeMereService.searchDemandes(
                        num, demandeur, type,
                        statutFilter,
                        dateFrom, dateTo,
                        page, size,
                        sort, dir
                );
            } else {
                demandesMeres = idsToUse.isEmpty()
                        ? demandeMereService.searchDemandesVisibleParUtilisateur(
                        num, demandeur, type, statutFilter,
                        dateFrom, dateTo,
                        List.of(-1),
                        page, size,
                        sort, dir
                )
                        : demandeMereService.searchDemandesVisibleParUtilisateur(
                        num, demandeur, type, statutFilter,
                        dateFrom, dateTo,
                        idsToUse,
                        page, size,
                        sort, dir
                );
            }

            model.addAttribute("statut", (statut == null) ? 0 : statut);
        }

        // ✅ Model commun
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("demandesMeres", demandesMeres);

        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);

        model.addAttribute("num", num == null ? "" : num);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("scope", scope);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());
        model.addAttribute("statutLabels", statutLabels);

        // ✅ flags de vue
        model.addAttribute("isMGOnly", isMG && !isAdmin);
        model.addAttribute("isFinanceOnly", isFinance && !isAdmin);
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

        boolean isFinance = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_FINANCE".equals(a.getAuthority()));

        boolean isSG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SG".equals(a.getAuthority()));

        boolean isAdminOrSpecial = isAdmin || isMG || isFinance || isSG;

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable : " + id);
            return "redirect:/demande/list";
        }

        Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;

        // ✅ Accès : Admin/MG/Finance/SG = OK ; sinon seulement (moi + enfants)
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        if (!isAdminOrSpecial) {
            if (demandeurId == null || !visibleIds.contains(demandeurId)) {
                redirectAttributes.addFlashAttribute("ko", "Accès refusé à cette demande.");
                return "redirect:/demande/list";
            }
        }

        // ✅ Enfants directs (sans moi)
        List<Integer> childrenIds = visibleIds.stream()
                .filter(x -> !x.equals(current.getId()))
                .toList();

        // ✅ N+1 du demandeur = la demande appartient à un de mes enfants
        boolean isViewerNplus1OfDemandeur = (demandeurId != null) && childrenIds.contains(demandeurId);

        // ✅ Droits de décision par niveau
        boolean canDecisionN1 = isViewerNplus1OfDemandeur && demande.getStatut() == StatutDemande.CREE;
        boolean canDecisionMG = isMG && demande.getStatut() == StatutDemande.VALIDATION_N1;
        boolean canDecisionFinance = isFinance && demande.getStatut() == StatutDemande.VALIDATION_N2;
        boolean canDecisionSG = isSG && demande.getStatut() == StatutDemande.VALIDATION_N3;

        // ✅ Lignes
        List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        // ✅ Labels
        Map<Integer, String> statutLabels = new LinkedHashMap<>();
        statutLabels.put(StatutDemande.CREE, "En attente N+1");
        statutLabels.put(StatutDemande.VALIDATION_N1, "En attente M.G.");
        statutLabels.put(StatutDemande.VALIDATION_N2, "En attente Finance");
        statutLabels.put(StatutDemande.VALIDATION_N3, "En attente S.G.");
        statutLabels.put(StatutDemande.VALIDE, "VALIDÉE");
        statutLabels.put(StatutDemande.REFUSE, "REFUSÉE");

        String statutLabel = statutLabels.getOrDefault(demande.getStatut(), "INCONNU");

        // ✅ statutHint (UI) : message adapté au viewer
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
                statutHint = "Vous avez validé — en attente de traitement par la Finance.";
            }
        }

        // Finance : soit en attente Finance (N2), soit déjà traité (N3)
        if (isFinance) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N2) {
                statutHint = "Demande en attente de votre validation (Finance).";
            } else if (demande.getStatut() == StatutDemande.VALIDATION_N3) {
                statutHint = "Vous avez validé — en attente de validation finale (SG).";
            }
        }

        // SG : soit en attente SG (N3), soit finalisé
        if (isSG) {
            if (demande.getStatut() == StatutDemande.VALIDATION_N3) {
                statutHint = "Demande en attente de votre validation finale (SG).";
            } else if (demande.getStatut() == StatutDemande.VALIDE) {
                statutHint = "Demande finalisée.";
            }
        }

        List<DemandePieceJointe> piecesJointes = demandePieceJointeService.listByDemandeId(demande.getId());
        model.addAttribute("piecesJointes", piecesJointes);

// 0=N+1, 1=MG, 2=Finance, 3=SG, 4=Terminé
        int currentStep = switch (demande.getStatut()) {
            case StatutDemande.CREE -> 0;              // En attente N+1
            case StatutDemande.VALIDATION_N1 -> 1;     // En attente MG
            case StatutDemande.VALIDATION_N2 -> 2;     // En attente Finance
            case StatutDemande.VALIDATION_N3 -> 3;     // En attente SG
            case StatutDemande.VALIDE -> 4;            // Terminé (validé)
            case StatutDemande.REFUSE -> -1;           // Terminé (refusé)
            default -> 0;
        };

        model.addAttribute("currentStep", currentStep);
        model.addAttribute("isRefused", demande.getStatut() == StatutDemande.REFUSE);
        model.addAttribute("isValidated", demande.getStatut() == StatutDemande.VALIDE);

        // ✅ Model (IMPORTANT : toujours envoyer les booléens)
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("demande", demande);
        model.addAttribute("lignes", lignes);

        model.addAttribute("canDecisionN1", canDecisionN1);
        model.addAttribute("canDecisionMG", canDecisionMG);
        model.addAttribute("canDecisionFinance", canDecisionFinance);
        model.addAttribute("canDecisionSG", canDecisionSG);

        model.addAttribute("statutLabels", statutLabels);
        model.addAttribute("statutLabel", statutLabel);
        model.addAttribute("statutHint", statutHint);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());

        return "demande/demande-fiche";
    }





    @PostMapping("/fiche/{id}/decision")
    public String decision(@PathVariable("id") String id,
                           @RequestParam("decision") String decision,
                           @RequestParam(value = "typeDemande", required = false) String typeDemande,
                           @RequestParam(value = "commentaire", required = false) String commentaire,
                           RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        boolean isMG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));

        boolean isFinance = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_FINANCE".equals(a.getAuthority()));

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

        // ✅ Autorisations par niveau
        boolean canDecisionN1 = demandeurId != null
                && childrenIds.contains(demandeurId)
                && demande.getStatut() == StatutDemande.CREE;

        boolean canDecisionMG = isMG && demande.getStatut() == StatutDemande.VALIDATION_N1;
        boolean canDecisionFinance = isFinance && demande.getStatut() == StatutDemande.VALIDATION_N2;
        boolean canDecisionSG = isSG && demande.getStatut() == StatutDemande.VALIDATION_N3;

        boolean allowed = isAdmin || canDecisionN1 || canDecisionMG || canDecisionFinance || canDecisionSG;

        if (!allowed) {
            redirectAttributes.addFlashAttribute("ko", "Cette demande ne peut pas être traitée par vous.");
            return "redirect:/demande/fiche/" + id;
        }

        String cmt = (commentaire == null) ? "" : commentaire.trim();

// ----- REJECT : commentaire obligatoire -----
        if ("REJECT".equals(action)) {
            if (cmt.isBlank()) {
                redirectAttributes.addFlashAttribute("ko", "Le commentaire est obligatoire pour rejeter une demande.");
                return "redirect:/demande/fiche/" + id;
            }


            demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.REFUSE);

            ValidationDemande validationDemande = new ValidationDemande();
            validationDemande.setStatut(StatutDemande.REFUSE);
            validationDemande.setDemandeMere(demande);
            validationDemande.setValidateur(current);
            validationDemande.setCommentaire(cmt);
            validationDemande.setDateAction(String.valueOf(LocalDateTime.now()));

            validationDemandeService.logAction(validationDemande);
            redirectAttributes.addFlashAttribute("ok", "Demande rejetée.");
            return "redirect:/demande/fiche/" + id;
        }


        // ----- APPROVE : transition selon le niveau -----
        if ("APPROVE".equals(action)) {

            if (canDecisionN1) {
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N1);
                logValidation(demande, current, StatutDemande.VALIDATION_N1, cmt);
                redirectAttributes.addFlashAttribute("ok", "Demande envoyée en validation N1 (MG).");
                return "redirect:/demande/fiche/" + id;
            }

            if (canDecisionMG) {
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

                // ✅ On enregistre le type puis on passe au statut suivant
                demandeMereService.saveDemandeMere(demande);
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
                logValidation(demande, current, StatutDemande.VALIDATION_N2, cmt);
                redirectAttributes.addFlashAttribute("ok", "Demande validée par les Moyens Généraux (N2).");
                return "redirect:/demande/fiche/" + id;
            }

            if (canDecisionFinance) {
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N3);
                logValidation(demande, current, StatutDemande.VALIDATION_N3, cmt);
                redirectAttributes.addFlashAttribute("ok", "Demande validée par la Finance (N3).");
                return "redirect:/demande/fiche/" + id;
            }

            if (canDecisionSG) {
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDE);
                logValidation(demande, current, StatutDemande.VALIDE, cmt);
                redirectAttributes.addFlashAttribute("ok", "Demande validée et finalisée (SG).");
                return "redirect:/demande/fiche/" + id;
            }

            // Admin : si tu veux le laisser forcer la suite même s'il n'est pas le bon rôle
            if (isAdmin) {
                int next = switch (demande.getStatut()) {
                    case StatutDemande.CREE -> StatutDemande.VALIDATION_N1;
                    case StatutDemande.VALIDATION_N1 -> StatutDemande.VALIDATION_N2;
                    case StatutDemande.VALIDATION_N2 -> StatutDemande.VALIDATION_N3;
                    case StatutDemande.VALIDATION_N3 -> StatutDemande.VALIDE;
                    default -> demande.getStatut();
                };
                demandeMereService.appliquerDecisionGlobale(demande, next);
                logValidation(demande, current, next, cmt);
                redirectAttributes.addFlashAttribute("ok", "Statut mis à jour (ADMIN).");
                return "redirect:/demande/fiche/" + id;
            }
        }

        redirectAttributes.addFlashAttribute("ko", "Décision invalide.");
        return "redirect:/demande/fiche/" + id;
    }

    @GetMapping("/files/{id}/{filename:.+}")
    public ResponseEntity<Resource> downloadDemandeFile(@PathVariable String id,
                                                        @PathVariable String filename) {

        Resource file = storageService.loadAsResource(filename);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }

    private void logValidation(DemandeMere demande, Utilisateur current, int statut, String commentaire) {
        ValidationDemande v = new ValidationDemande();
        v.setStatut(statut);
        v.setDemandeMere(demande);
        v.setValidateur(current);
        v.setCommentaire((commentaire == null) ? null : commentaire.trim());
        v.setDateAction(String.valueOf(LocalDateTime.now()));
        validationDemandeService.logAction(v);
    }


}
