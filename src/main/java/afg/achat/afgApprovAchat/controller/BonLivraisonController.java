package afg.achat.afgApprovAchat.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/bonlivraison")
public class BonLivraisonController {
    @GetMapping("/list")
    public String getAllBonLivraisons(Model model) {

        return "bl/bl-liste";
    }

    @GetMapping("/add")
    public String addBonLivraisonPage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        return "bl/bl-saisie";
    }
}
