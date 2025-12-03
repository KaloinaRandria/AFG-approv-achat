package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.naming.NamingException;

@Controller
@RequestMapping("/")
public class HomeController {

    @GetMapping("/admin")
    public String home() {
        return "redirect:/admin/article/list";
    }

    @RequestMapping(value="")
    public String empty_cont(Model model, RedirectAttributes redirAttrs) throws NamingException {
        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (user.getRoles().contains("ROLE_ADMIN")) {
            return "redirect:/admin";
        }
        else {
            return "redirect:/user";
        }
    }

    @GetMapping("/login-error")
    public String login(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        String errorMessage = null;
        if (session != null) {
            AuthenticationException ex = (AuthenticationException) session
                    .getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            if (ex != null) {
                errorMessage = ex.getMessage();
            }
        }
        model.addAttribute("errorMessage", errorMessage);
        return "login";
    }
}
