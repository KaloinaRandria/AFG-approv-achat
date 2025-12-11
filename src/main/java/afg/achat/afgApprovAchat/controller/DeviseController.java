package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/devise")
public class DeviseController {
    @Autowired
    DeviseService deviseService;

    @GetMapping("/devise-pages")
    public String goToDevisePage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());

        Devise[] listeDevises = deviseService.getAllDevises();
        model.addAttribute("devises", listeDevises);
        model.addAttribute("devise", new Devise());

        return "devise/devise-saisie-liste";
    }

    @PostMapping("save-devise")
    public String insertDevise(@RequestParam(name = "acronyme") String acronyme,
                               @RequestParam(name = "designation") String designation,
                               @RequestParam(name = "coursAriary") String coursAriary,
                               RedirectAttributes redirectAttributes) {
        Devise devise = new Devise();
        try {
            devise.setAcronyme(acronyme);
            devise.setDesignation(designation);
            devise.setCoursAriary(coursAriary);
            devise.setDateMiseAJour(String.valueOf(LocalDateTime.now()));
            deviseService.insertDevise(devise);
            redirectAttributes.addFlashAttribute("ok", "Devise "
                    + devise.getDesignation() + " ajoutée avec succès. (Acronyme : " + devise.getAcronyme() + ")");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de l'ajout du devise: " + e.getMessage());
            redirectAttributes.addFlashAttribute("devise", devise);
            return "redirect:/admin/devise/devise-pages";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de l'ajout du devise: " + e.getMessage());
            redirectAttributes.addFlashAttribute("devise", devise);
            return "redirect:/admin/devise/devise-pages";
        }
        return "redirect:/admin/devise/devise-pages";
    }
}
