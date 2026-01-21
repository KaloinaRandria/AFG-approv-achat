package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.service.utilisateur.ResetData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reset-data")
public class ResetDataController {
    @Autowired
    ResetData resetData;
    @GetMapping
    public String resetData(RedirectAttributes redirectAttributes) {
        try {
            resetData.initBase();
            redirectAttributes.addFlashAttribute("ok", "Données réinitialisées avec succès.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de la réinitialisation des données : " + e.getMessage());
            return "redirect:/";
        }
        
        return "redirect:/";
    }
}
