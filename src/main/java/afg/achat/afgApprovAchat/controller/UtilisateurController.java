package afg.achat.afgApprovAchat.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
@PreAuthorize("hasAnyRole('ADMIN')")
public class UtilisateurController {
    @GetMapping("/add")
    public String addUserPage(Model model) {
        return "utilisateur/utilisateur-saisie";
    }

}
