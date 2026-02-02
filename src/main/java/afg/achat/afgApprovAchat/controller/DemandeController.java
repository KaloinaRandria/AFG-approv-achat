package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.util.AdresseService;
import afg.achat.afgApprovAchat.service.util.DepartementService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/demande")
public class DemandeController {
    @Autowired
    AdresseService adresseService;
    @Autowired
    DepartementService departementService;
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
            DemandeMere demandeMere = new DemandeMere();
            demandeMere.setId(idGenerator);
            demandeMere.setDateDemande(String.valueOf(LocalDateTime.now()));
            demandeMere.setDateSortie(dateSortie);
            demandeMere.setMotifEvoque(motif);
            demandeMere.setDemandeur(utilisateur);
            demandeMere.setDescription(description);
            demandeMere.setStatut(1);

            this.demandeMereService.saveDemandeMere(demandeMere);

            List<DemandeFille> demandeFilles = new ArrayList<>();
            for (int i = 0; i < articleCodes.size(); i++) {
                DemandeFille demandeFille = new DemandeFille();
                demandeFille.setDemandeMere(demandeMere);
                int finalI = i;
                demandeFille.setArticle(articleService.getArticleByCodeArticle(articleCodes.get(i))
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable : " + articleCodes.get(finalI))));
                demandeFille.setQuantite(quantite.get(i));
                demandeFille.setStatut(1);
                demandeFilles.add(demandeFille);

                this.demandeFilleService.saveDemandeFille(demandeFille);
            }

            if (articleCodes.isEmpty()) {
                redirectAttributes.addFlashAttribute(
                        "ko",
                        "Impossible de valider la demande sans aucune ligne d’article."
                );
                return "redirect:/demande/add";
            }

            boolean hasValideLine = false;
            for (int i = 0; i < articleCodes.size(); i++) {
                if (quantite.get(i) != null && !quantite.get(i).isEmpty()) {
                    try {
                        double qte = Double.parseDouble(quantite.get(i));
                        if (qte > 0) {
                            hasValideLine = true;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (!hasValideLine) {
                redirectAttributes.addFlashAttribute(
                        "ko",
                        "Veuillez saisir au moins une ligne avec une quantité reçue valide."
                );
                return "redirect:/demande/add";
            }

            redirectAttributes.addFlashAttribute("ok", "Demande enregistrée avec succès.");
            return "redirect:/demande/list";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/demande/add";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'enregistrement de la demande : " + e.getMessage());
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

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isMG = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MOYENS_GENERAUX"));
        boolean isAdminOrMG = isAdmin || isMG;

        // ✅ Labels (table)
        Map<Integer, String> statutLabels = Map.of(
                StatutDemande.CREE, "CRÉÉE",
                StatutDemande.VALIDATION_N1, "EN_VALIDATION N1",
                StatutDemande.VALIDATION_N2, "EN_VALIDATION N2",
                StatutDemande.VALIDATION_N3, "EN_VALIDATION N3",
                StatutDemande.VALIDE, "VALIDÉE",
                StatutDemande.REFUSE, "REFUSÉE"
        );

        // ✅ Filtre (select)
        Map<Integer, String> statutFiltre = new LinkedHashMap<>();
        statutFiltre.put(StatutDemande.CREE, "CRÉÉE");
        statutFiltre.put(StatutDemande.VALIDATION_N1, "EN_VALIDATION N1");
        statutFiltre.put(StatutDemande.VALIDATION_N2, "EN_VALIDATION N2");
        statutFiltre.put(StatutDemande.VALIDE, "VALIDÉE");
        statutFiltre.put(StatutDemande.REFUSE, "REFUSÉE");

        model.addAttribute("statutFiltre", statutFiltre);

        // ✅ Normalisation (0/null => pas de filtre)
        Integer statutFilter = (statut == null || statut == 0) ? null : statut;

        // ✅ Visibilité (moi + enfants)
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        boolean hasChildren = visibleIds.size() > 1;

        boolean showDemandeurColumn = isAdminOrMG || hasChildren;
        model.addAttribute("showDemandeurColumn", showDemandeurColumn);

        boolean showScopeFilter = hasChildren && !isAdminOrMG;
        model.addAttribute("showDemandeurScopeFilter", showScopeFilter);

        // ✅ Scope (uniquement non admin/MG)
        List<Integer> idsToUse = visibleIds;
        if (!isAdminOrMG) {
            if ("ME".equalsIgnoreCase(scope)) {
                idsToUse = List.of(current.getId());
            } else if ("CHILDREN".equalsIgnoreCase(scope)) {
                idsToUse = visibleIds.stream()
                        .filter(idU -> !idU.equals(current.getId()))
                        .toList();
            }
        }

        Page<DemandeMere> demandesMeres;

        // ✅ Cas MG : doit voir VALIDATION_N1 + VALIDATION_N2
        if (isMG && !isAdmin) {

            // 1) on récupère tout (sans pagination) pour N1 et N2
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

            // 2) merge + tri (ex: dateDemande desc)
            List<DemandeMere> merged = new java.util.ArrayList<>();
            merged.addAll(p1.getContent());
            merged.addAll(p2.getContent());

            // tri simple (si dateDemande existe)
            merged.sort(Comparator.comparing(DemandeMere::getDateDemande).reversed());

            // 3) pagination manuelle
            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), merged.size());

            List<DemandeMere> slice = (start > end) ? List.of() : merged.subList(start, end);
            demandesMeres = new PageImpl<>(slice, pageable, merged.size());

            // on force le select "Tous" car MG est filtré automatiquement
            model.addAttribute("statut", 0);

        } else {
            // ✅ Cas normal : un seul statut (ou null)
            if (isAdminOrMG) {
                demandesMeres = demandeMereService.searchDemandes(
                        num, demandeur, type,
                        statutFilter,
                        dateFrom, dateTo,
                        page, size,
                        sort, dir
                );
            } else {
                // non admin/MG => visibleParUtilisateur (si tu as la méthode)
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

        model.addAttribute("isMGOnly", isMG && !isAdmin);

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
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isMG = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MOYENS_GENERAUX"));
        boolean isAdminOrMG = isAdmin || isMG;

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable : " + id);
            return "redirect:/demande/list";
        }

        Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;

        // ✅ Accès : Admin/MG = OK ; sinon seulement (moi + enfants)
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        if (!isAdminOrMG) {
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

        // ✅ N+1 peut décider uniquement si : demande d’un enfant + statut = CREE
        boolean canDecision = isViewerNplus1OfDemandeur
                && demande.getStatut() == StatutDemande.CREE;

        // ✅ MG peut décider uniquement si : statut = VALIDATION_N1
        boolean canDecisionMG = isMG
                && demande.getStatut() == StatutDemande.VALIDATION_N1;

        // ✅ Lignes
        List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        // ✅ Labels
        Map<Integer, String> statutLabels = new LinkedHashMap<>();
        statutLabels.put(StatutDemande.CREE, "CRÉÉE");
        statutLabels.put(StatutDemande.VALIDATION_N1, "EN VALIDATION N1");
        statutLabels.put(StatutDemande.VALIDATION_N2, "EN VALIDATION N2");
        statutLabels.put(StatutDemande.VALIDATION_N3, "EN VALIDATION N3");
        statutLabels.put(StatutDemande.VALIDE, "VALIDÉE");
        statutLabels.put(StatutDemande.REFUSE, "REFUSÉE");

        // ✅ statutLabel
        String statutLabel = statutLabels.getOrDefault(demande.getStatut(), "INCONNU");

        // ✅ statutHint (UI)
        String statutHint = null;

        // N+1 : il a déjà validé (ça arrive quand statut = VALIDATION_N1)
        if (isViewerNplus1OfDemandeur && demande.getStatut() == StatutDemande.VALIDATION_N1) {
            statutHint = "Vous avez validé — en attente de traitement par les Moyens Généraux.";
        }

        // MG : il voit que la demande est en attente de SON traitement
        if (isMG && demande.getStatut() == StatutDemande.VALIDATION_N1) {
            statutHint = "Demande en attente de votre validation (Moyens Généraux).";
        }

        // ✅ Model (IMPORTANT : mettre les 2 booléens pour Thymeleaf)
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("demande", demande);
        model.addAttribute("lignes", lignes);

        model.addAttribute("canDecision", canDecision);
        model.addAttribute("canDecisionMG", canDecisionMG);

        model.addAttribute("statutLabels", statutLabels); // optionnel
        model.addAttribute("statutLabel", statutLabel);
        model.addAttribute("statutHint", statutHint);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());

        return "demande/demande-fiche";
    }




    @PostMapping("/fiche/{id}/decision")
    public String decision(@PathVariable("id") String id,
                           @RequestParam("decision") String decision,
                           RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        boolean isMG = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        // Normaliser la décision (sécurise null + espaces)
        String action = (decision == null) ? "" : decision.trim().toUpperCase();

        // Récupérer l'id du demandeur (si null => impossible de décider sauf admin)
        Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;

        // Enfants directs du current
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());
        List<Integer> childrenIds = visibleIds.stream()
                .filter(x -> !x.equals(current.getId()))
                .toList();

        // Règles de décision
        boolean canDecisionN1 = demandeurId != null
                && childrenIds.contains(demandeurId)
                && demande.getStatut() == StatutDemande.CREE;

        boolean canDecisionMG = isMG
                && demande.getStatut() == StatutDemande.VALIDATION_N1;

        // Admin : droit total (si tu veux qu'il respecte le workflow, enlève "isAdmin" ci-dessous)
        boolean allowed = isAdmin || canDecisionN1 || canDecisionMG;

        if (!allowed) {
            redirectAttributes.addFlashAttribute("ko", "Cette demande ne peut pas être traitée par vous.");
            return "redirect:/demande/fiche/" + id;
        }

        // ----- Traitement REJECT -----
        if ("REJECT".equals(action)) {
            demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.REFUSE);
            redirectAttributes.addFlashAttribute("ok", "Demande rejetée.");
            return "redirect:/demande/fiche/" + id;
        }

        // ----- Traitement APPROVE -----
        if ("APPROVE".equals(action)) {

            // N+1 valide -> envoie en VALIDATION_N1
            if (canDecisionN1) {
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N1);
                redirectAttributes.addFlashAttribute("ok", "Demande envoyée en validation N1.");
                return "redirect:/demande/fiche/" + id;
            }

            // MG (ou Admin) valide -> passe au niveau suivant (à adapter)
            if (canDecisionMG || isAdmin) {
                demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
                redirectAttributes.addFlashAttribute("ok", "Demande validée par les Moyens Généraux.");
                return "redirect:/demande/fiche/" + id;
            }
        }

        redirectAttributes.addFlashAttribute("ko", "Décision invalide.");
        return "redirect:/demande/fiche/" + id;
    }


}
