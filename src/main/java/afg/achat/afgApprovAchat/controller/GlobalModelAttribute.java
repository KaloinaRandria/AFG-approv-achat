package afg.achat.afgApprovAchat.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttribute {

    @ModelAttribute
    public void addCurrentUri(HttpServletRequest request, Model model) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String cleanUri = uri.substring(contextPath.length());
//        System.out.println("=== Context PATH: " + contextPath);
//        System.out.println("=== Current URI: " + cleanUri);

        model.addAttribute("currentUri", cleanUri);
    }
}