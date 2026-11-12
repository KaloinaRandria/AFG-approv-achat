package afg.achat.afgApprovAchat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/error")
public class CustomErrorController {

    // Tous verbes : un refus (rôle ou jeton CSRF) sur un POST est transmis ici en POST
    @RequestMapping("/403")
    public String error403() {
        return "error/403";
    }

    @GetMapping("/401")
    public String error401() {
        return "error/401";
    }
}
