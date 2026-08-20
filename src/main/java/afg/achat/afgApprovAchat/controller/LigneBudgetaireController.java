package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ligne-budgetaire")
@RequiredArgsConstructor
public class LigneBudgetaireController {
    private final CentreBudgetaireService centreBudgetaireService;

    @GetMapping("/ligne-budgetaire-pages")
    public String goToligneBudgetairePage(Model model) {
        CentreBudgetaire[] centreBudgetaires = centreBudgetaireService.getAllCentreBudgetaires();
        model.addAttribute("centreBudgetaires", centreBudgetaires);
        model.addAttribute("centreBudgetaire", new CentreBudgetaire());
        return "ligne/ligne-saisie-liste";
    }

    @PostMapping("/save-ligne-budgetaire")
    public String insertLigneBudgetaire(@RequestParam(name = "codeCentre") String codeCentre,
                                        @RequestParam(name = "description") String description,
                                        RedirectAttributes redirectAttributes) {
        CentreBudgetaire centreBudgetaire = new CentreBudgetaire();

        if (codeCentre == null || codeCentre.trim().isEmpty()) {
            return redirectWithError(redirectAttributes, centreBudgetaire,
                    "Erreur de validation : Le code ligne est obligatoire.");
        }
        if (description == null || description.trim().isEmpty()) {
            return redirectWithError(redirectAttributes, centreBudgetaire,
                    "Erreur de validation : La description est obligatoire.");
        }

        try {
            codeCentre = codeCentre.trim().toUpperCase();
            description = description.trim();

            centreBudgetaire.setCodeCentre(codeCentre);
            centreBudgetaire.setDescription(description);

            centreBudgetaireService.insertCentreBudgetaire(centreBudgetaire);

            redirectAttributes.addFlashAttribute("ok",
                    "Ligne budgétaire ajoutée avec succès. (Code : " + codeCentre + ")");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de l'ajout de la ligne budgétaire: " + e.getMessage());
            redirectAttributes.addFlashAttribute("centreBudgetaire", centreBudgetaire);
            return "redirect:/ligne-budgetaire/ligne-budgetaire-pages";
        }

        return "redirect:/ligne-budgetaire/ligne-budgetaire-pages";
    }

    private String redirectWithError(RedirectAttributes redirectAttributes,
                                     CentreBudgetaire centreBudgetaire,
                                     String message) {
        redirectAttributes.addFlashAttribute("ko", message);
        redirectAttributes.addFlashAttribute("centreBudgetaire", centreBudgetaire);
        return "redirect:/ligne-budgetaire/ligne-budgetaire-pages";
    }


}
