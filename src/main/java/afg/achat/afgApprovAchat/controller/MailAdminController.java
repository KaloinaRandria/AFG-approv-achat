package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.email.MailCredentialService;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/mail-config")
@PreAuthorize("hasAnyRole('ADMIN','IT')")
public class MailAdminController {

    private final MailCredentialService service;

    public MailAdminController(MailCredentialService service) {
        this.service = service;
    }

    @GetMapping
    public String afficherPage(Model model) {
        model.addAttribute("historique", service.getHistorique());
        model.addAttribute("derniereModification", service.getDateDerniereModification());
        model.addAttribute("expirationProche", service.motDePasseExpireBientot(80));
        return "email/mail-config";
    }

    @PostMapping("/update")
    public String mettreAJour(@RequestParam String username,
                              @RequestParam String nouveauMotDePasse,
                              @RequestParam String confirmationMotDePasse,
                              Model model,
                              Authentication authentication) {

        if (!nouveauMotDePasse.equals(confirmationMotDePasse)) {
            model.addAttribute("erreur", "Les deux mots de passe ne correspondent pas.");
            model.addAttribute("historique", service.getHistorique());
            model.addAttribute("derniereModification", service.getDateDerniereModification());
            return "email/mail-config";
        }

        Utilisateur admin = (Utilisateur) authentication.getPrincipal();
        service.changerMotDePasse(username, nouveauMotDePasse, admin);

        return "redirect:/admin/mail-config?succes";
    }
}