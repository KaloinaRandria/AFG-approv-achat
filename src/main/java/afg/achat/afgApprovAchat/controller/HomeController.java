package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.naming.NamingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/")
public class HomeController {
    @Autowired
    UtilisateurService utilisateurService;
    @Autowired
    DemandeMereService demandeMereService;
    @GetMapping
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login-error")
    public String login(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        String errorMessage = null;
        if (session != null) {
            AuthenticationException ex = (AuthenticationException) session
                    .getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            if (ex != null) {
                errorMessage = ex.getMessage();
            }
        }
        model.addAttribute("errorMessage", errorMessage);
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Model model,
            HttpServletRequest request) {

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

        // ── 1. Récupération du périmètre visible (hiérarchie + validateurs) ─
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());

        // Récupérer les IDs des utilisateurs que current doit valider
        List<Integer> idsAValider = utilisateurService.getIdsUtilisateursAValider(current.getId());

        // Fusionner les deux périmètres
        Set<Integer> allAccessibleIds = new HashSet<>(visibleIds);
        allAccessibleIds.addAll(idsAValider);
        List<Integer> finalVisibleIds = new ArrayList<>(allAccessibleIds);

        // Séparer les enfants hiérarchiques des utilisateurs à valider
        List<Integer> childrenIds = visibleIds.stream()
                .filter(x -> !x.equals(current.getId()))
                .toList();

        // ── 2. Récupération de TOUTES les demandes selon le rôle ────────
        DemandeMere[] toutes;
        if (isAdminOrSpecial) {
            toutes = demandeMereService.getAllDemandesMeres();
        } else {
            toutes = Arrays.stream(demandeMereService.getAllDemandesMeres())
                    .filter(d -> d.getDemandeur() != null
                            && finalVisibleIds.contains(d.getDemandeur().getId()))
                    .toArray(DemandeMere[]::new);
        }

        // ── 3. Application du filtre date (APRÈS récupération) ──────────
        if (dateFrom != null || dateTo != null) {
            final LocalDateTime from = (dateFrom != null)
                    ? dateFrom.atStartOfDay()
                    : LocalDate.of(1900, 1, 1).atStartOfDay();
            final LocalDateTime to = (dateTo != null)
                    ? dateTo.atTime(23, 59, 59)
                    : LocalDate.of(2999, 12, 31).atTime(23, 59, 59);

            toutes = Arrays.stream(toutes)
                    .filter(d -> d.getDateDemande() != null
                            && !d.getDateDemande().isBefore(from)
                            && !d.getDateDemande().isAfter(to))
                    .toArray(DemandeMere[]::new);
        }

        // ── 4. Compteurs principaux ──────────────────────────────────────
        long enCours = Arrays.stream(toutes)
                .filter(d -> d.getStatut() != StatutDemande.VALIDE
                        && d.getStatut() != StatutDemande.REFUSE)
                .count();
        long refusees = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.REFUSE)
                .count();
        long terminees = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.VALIDE)
                .count();

        model.addAttribute("enCours",   enCours);
        model.addAttribute("refusees",  refusees);
        model.addAttribute("terminees", terminees);

        // ── 5. Données mensuelles ────────────────────────────────────────
        record MoisData(int annee, int mois, long enCours, long refusees, long terminees) {}

        Map<String, long[]> parMois = new LinkedHashMap<>();

        for (DemandeMere d : toutes) {
            if (d.getDateDemande() == null) continue;
            int annee = d.getDateDemande().getYear();
            int mois  = d.getDateDemande().getMonthValue();
            String key = annee + "-" + String.format("%02d", mois);
            parMois.computeIfAbsent(key, k -> new long[3]);

            if (d.getStatut() == StatutDemande.REFUSE) {
                parMois.get(key)[1]++;
            } else if (d.getStatut() == StatutDemande.VALIDE) {
                parMois.get(key)[2]++;
            } else {
                parMois.get(key)[0]++;
            }
        }

        List<Map<String, Object>> moisData = parMois.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mois",      e.getKey());
                    m.put("enCours",   e.getValue()[0]);
                    m.put("refusees",  e.getValue()[1]);
                    m.put("terminees", e.getValue()[2]);
                    return m;
                })
                .collect(Collectors.toList());

        model.addAttribute("moisData", moisData);

        // ── 6. Nature OPEX / CAPEX ───────────────────────────────────────
        long opex = Arrays.stream(toutes)
                .filter(d -> DemandeMere.NatureDemande.OPEX == d.getNatureDemande()).count();
        long capex = Arrays.stream(toutes)
                .filter(d -> DemandeMere.NatureDemande.CAPEX == d.getNatureDemande()).count();

        model.addAttribute("opex",  opex);
        model.addAttribute("capex", capex);

        // ── 7. Priorité ──────────────────────────────────────────────────
        long p0 = Arrays.stream(toutes)
                .filter(d -> DemandeMere.PrioriteDemande.P0 == d.getPriorite()).count();
        long p1 = Arrays.stream(toutes)
                .filter(d -> DemandeMere.PrioriteDemande.P1 == d.getPriorite()).count();
        long p2 = Arrays.stream(toutes)
                .filter(d -> DemandeMere.PrioriteDemande.P2 == d.getPriorite()).count();

        model.addAttribute("p0", p0);
        model.addAttribute("p1", p1);
        model.addAttribute("p2", p2);

        // ── 8. Demandes en attente par niveau de validation ──────────────
        long attenteN1  = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.CREE).count();
        long attenteN2  = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.VALIDATION_N1).count();
        long attenteN3  = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.VALIDATION_N2).count();
        long attenteN4  = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.VALIDATION_N3).count();
        long attenteSG  = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.VALIDATION_N4).count();
        long attenteCodep = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.DECISION_CODEP).count();

        model.addAttribute("attenteN1",    attenteN1);
        model.addAttribute("attenteN2",    attenteN2);
        model.addAttribute("attenteN3",    attenteN3);
        model.addAttribute("attenteN4",    attenteN4);
        model.addAttribute("attenteSG",    attenteSG);
        model.addAttribute("attenteCodep", attenteCodep);

        // 9. Demandes spécifiques à l'utilisateur courant ─────────────────
        // Demandes que je dois valider (en tant que validateur assigné ou N+1)
        long mesDemandesAValider = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.CREE)
                .filter(d -> {
                    if (d.getDemandeur() == null) return false;
                    Integer demandeurId = d.getDemandeur().getId();
                    // Vérifier si je suis N+1 ou validateur assigné
                    return childrenIds.contains(demandeurId) || idsAValider.contains(demandeurId);
                })
                .count();

        // Demandes que je dois valider en tant que validateur assigné uniquement
        long demandesValidateurAssigné = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.CREE)
                .filter(d -> d.getDemandeur() != null && idsAValider.contains(d.getDemandeur().getId()))
                .filter(d -> !childrenIds.contains(d.getDemandeur().getId())) // Exclure ceux déjà N+1
                .count();

        // Demandes de mes subordonnés hiérarchiques (N+1)
        long demandesNPlusUn = Arrays.stream(toutes)
                .filter(d -> d.getStatut() == StatutDemande.CREE)
                .filter(d -> d.getDemandeur() != null && childrenIds.contains(d.getDemandeur().getId()))
                .count();

        model.addAttribute("mesDemandesAValider", mesDemandesAValider);
        model.addAttribute("demandesValidateurAssigné", demandesValidateurAssigné);
        model.addAttribute("demandesNPlusUn", demandesNPlusUn);
        model.addAttribute("nbUtilisateursAValider", idsAValider.size());
        model.addAttribute("nbSubordonnes", childrenIds.size());

        // 10. Indicateur si l'utilisateur a des demandes à valider ─────────
        boolean hasDemandesAValider = mesDemandesAValider > 0;
        model.addAttribute("hasDemandesAValider", hasDemandesAValider);

        // ── 11. Infos contextuelles ───────────────────────────────────────
        model.addAttribute("dateFrom",        dateFrom);
        model.addAttribute("dateTo",          dateTo);
        model.addAttribute("currentUser",     current);
        model.addAttribute("isAdminOrSpecial", isAdminOrSpecial);

        return "dashboard";
    }
}
