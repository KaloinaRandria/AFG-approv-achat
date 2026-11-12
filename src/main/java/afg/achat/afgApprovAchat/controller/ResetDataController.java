package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.service.utilisateur.ResetData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Réinitialisation des données (TRUNCATE) : outil de développement uniquement.
 * Le contrôleur n'existe que sous le profil "dev", est réservé aux administrateurs
 * et n'accepte que POST (protégé par le jeton CSRF).
 */
@Controller
@Profile("dev")
@RequestMapping("/reset-data")
@PreAuthorize("hasRole('ADMIN')")
public class ResetDataController {
    @Autowired
    ResetData resetData;

    @PostMapping
    public String resetData(RedirectAttributes redirectAttributes) {
        try {
            resetData.initBase();
            redirectAttributes.addFlashAttribute("ok", "Données réinitialisées avec succès.");
            return "redirect:/";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de la réinitialisation des données : " + e.getMessage());
            return "redirect:/";
        }
    }
}
