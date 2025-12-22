package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.service.FournisseurService;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/bonlivraison")
public class BonLivraisonController {
    @Autowired
    FournisseurService fournisseurService;
    @Autowired
    DeviseService deviseService;

    @GetMapping("/list")
    public String getAllBonLivraisons(Model model) {

        return "bl/bl-liste";
    }

    @GetMapping("/add")
    public String addBonLivraisonPage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());

        model.addAttribute("fournisseurs", fournisseurService.getAllFournisseurs());
        model.addAttribute("devises", deviseService.getAllDevises());
        return "bl/bl-saisie";
    }
}
