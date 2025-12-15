package afg.achat.afgApprovAchat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/bonlivraison")
public class BonLivraisonController {
    @GetMapping("/list")
    public String getAllBonLivraisons(Model model) {

        return "bonlivraison/bonlivraison-list";
    }

    @GetMapping("/form")
    public String goToBonLivraisonForm(Model model) {
        return "bonlivraison/bonlivraison-form";
    }
}
