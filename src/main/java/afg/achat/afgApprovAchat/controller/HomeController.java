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
import java.util.Arrays;
import java.util.List;

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

        boolean isAdminOrSpecial = isAdmin || isMG || isControleur || isDFC;

        // ── 1. Récupération du périmètre visible ────────────────────────
        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(current.getId());

        // ── 2. Récupération de TOUTES les demandes selon le rôle ────────
        DemandeMere[] toutes;
        if (isAdminOrSpecial) {
            toutes = demandeMereService.getAllDemandesMeres();
        } else {
            toutes = Arrays.stream(demandeMereService.getAllDemandesMeres())
                    .filter(d -> d.getDemandeur() != null
                            && visibleIds.contains(d.getDemandeur().getId()))
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
        // Si filtre date actif → année dynamique, sinon année courante
        int currentYear = (dateFrom != null) ? dateFrom.getYear() : LocalDate.now().getYear();

        long[] enCoursMois   = new long[12];
        long[] refuseesMois  = new long[12];
        long[] termineesMois = new long[12];

        for (DemandeMere d : toutes) {
            if (d.getDateDemande() == null) continue;
            int mois = d.getDateDemande().getMonthValue() - 1; // 0-based

            if (d.getStatut() == StatutDemande.REFUSE) {
                refuseesMois[mois]++;
            } else if (d.getStatut() == StatutDemande.VALIDE) {
                termineesMois[mois]++;
            } else {
                enCoursMois[mois]++;
            }
        }

        model.addAttribute("enCoursMois",   Arrays.toString(enCoursMois));
        model.addAttribute("refuseesMois",  Arrays.toString(refuseesMois));
        model.addAttribute("termineesMois", Arrays.toString(termineesMois));

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

        // ── 8. État de livraison ─────────────────────────────────────────
        long nonLivree = Arrays.stream(toutes)
                .filter(d -> DemandeMere.EtatLivraison.NON_LIVREE == d.getEtatLivraison()).count();
        long partielle = Arrays.stream(toutes)
                .filter(d -> DemandeMere.EtatLivraison.PARTIELLE == d.getEtatLivraison()).count();
        long livree = Arrays.stream(toutes)
                .filter(d -> DemandeMere.EtatLivraison.LIVREE == d.getEtatLivraison()).count();

        model.addAttribute("nonLivree", nonLivree);
        model.addAttribute("partielle", partielle);
        model.addAttribute("livree",    livree);

        // ── 9. Infos contextuelles ───────────────────────────────────────
        model.addAttribute("dateFrom",        dateFrom);
        model.addAttribute("dateTo",          dateTo);
        model.addAttribute("currentUri",      request.getRequestURI());
        model.addAttribute("currentUser",     current);
        model.addAttribute("isAdminOrSpecial", isAdminOrSpecial);
        model.addAttribute("annee",           currentYear);

        return "dashboard";
    }
}
