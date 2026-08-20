package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.DTO.DashboardStatsDTO;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.DashboardService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private final DashboardService dashboardService;
    private final UtilisateurService utilisateurService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur current = utilisateurService.getUtilisateurByMail(
                ((Utilisateur) auth.getPrincipal()).getMail());

        boolean isAdminOrSpecial = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> Set.of(
                        "ROLE_ADMIN","ROLE_MOYENS_GENERAUX","ROLE_CONTROLEUR",
                        "ROLE_DFC","ROLE_SG"
                ).contains(r));

        List<Integer> visibleIds  = utilisateurService.getIdsUtilisateurVisible(current.getId());
        List<Integer> idsAValider = utilisateurService.getIdsUtilisateursAValider(current.getId());

        Set<Integer> allIds = new HashSet<>(visibleIds);
        allIds.addAll(idsAValider);

        DashboardStatsDTO stats = dashboardService.computeStats(
                new ArrayList<>(allIds), isAdminOrSpecial, dateFrom, dateTo, current.getId());

        return ResponseEntity.ok(stats);
    }
}
