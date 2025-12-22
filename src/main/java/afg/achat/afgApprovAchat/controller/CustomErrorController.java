package afg.achat.afgApprovAchat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/error")
public class CustomErrorController {

    @GetMapping("/403")
    public String error403() {
        return "error/403";
    }

    @GetMapping("/401")
    public String error401() {
        return "error/401";
    }
}
