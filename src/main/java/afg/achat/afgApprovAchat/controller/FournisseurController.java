package afg.achat.afgApprovAchat.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/fournisseur")
public class FournisseurController {
    @GetMapping("/add")
    public String addFournisseurPage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        return "fournisseur/fournisseur-saisie";
    }

    @PostMapping("/save")
    public String insertFournisseur(Model model, RedirectAttributes redirectAttributes) {

        return "fournisseur/fournisseur-saisie";
    }
}
