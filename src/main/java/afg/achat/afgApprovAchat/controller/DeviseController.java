package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
}
