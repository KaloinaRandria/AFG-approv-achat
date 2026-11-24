package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.service.FamilleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Paramétrage des familles d'articles : Moyens Généraux et administrateur. */
@Controller
@RequestMapping("/famille")
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
@RequiredArgsConstructor
public class FamilleController {

    private static final String PAGE = "redirect:/famille";

    private final FamilleService familleService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("familles", familleService.getFamillesTriees());
        model.addAttribute("nbArticles", familleService.nombreArticlesParFamille());
        return "famille/famille-liste";
    }

    @PostMapping("/ajouter")
    public String ajouter(@RequestParam(name = "description", required = false) String description,
                          RedirectAttributes redirectAttributes) {
        try {
            Famille f = familleService.creer(description);
            redirectAttributes.addFlashAttribute("ok", "Famille « " + f.getDescription() + " » ajoutée.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return PAGE;
    }

    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable int id,
                           @RequestParam(name = "description", required = false) String description,
                           RedirectAttributes redirectAttributes) {
        try {
            Famille f = familleService.renommer(id, description);
            redirectAttributes.addFlashAttribute("ok", "Famille renommée en « " + f.getDescription() + " ».");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return PAGE;
    }

    /**
     * Fusionne les familles sélectionnées dans la famille conservée : leurs articles y passent,
     * puis elles sont supprimées. Tout ou rien.
     */
    @PostMapping("/fusionner")
    public String fusionner(@RequestParam(name = "sources", required = false) java.util.List<Integer> sources,
                            @RequestParam(name = "cible", required = false) Integer cible,
                            RedirectAttributes redirectAttributes) {
        if (cible == null) {
            redirectAttributes.addFlashAttribute("ko", "Choisissez la famille à conserver.");
            return PAGE;
        }
        String destination = familleService.getFamilleById(cible).map(Famille::getDescription).orElse(null);
        try {
            int nbFamilles = (int) (sources == null ? 0 : sources.stream().distinct().filter(id -> !id.equals(cible)).count());
            int nbArticles = familleService.fusionnerPlusieurs(sources, cible, auteurCourant());
            redirectAttributes.addFlashAttribute("ok", nbFamilles + " famille(s) fusionnée(s) dans « "
                    + destination + " » : " + nbArticles + " article(s) déplacé(s).");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Une des familles est encore utilisée ailleurs : fusion annulée, rien n'a été modifié.");
        }
        return PAGE;
    }

    private static String auteurCourant() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof afg.achat.afgApprovAchat.model.utilisateur.Utilisateur u) {
            return u.getNom() + " " + u.getPrenom();
        }
        return "Admin";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable int id, RedirectAttributes redirectAttributes) {
        try {
            Famille f = familleService.supprimer(id);
            redirectAttributes.addFlashAttribute("ok", "Famille « " + f.getDescription() + " » supprimée.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Encore référencée ailleurs (autre famille rattachée, historique…)
            redirectAttributes.addFlashAttribute("ko", "Cette famille est encore utilisée : elle ne peut pas être supprimée.");
        }
        return PAGE;
    }
}
