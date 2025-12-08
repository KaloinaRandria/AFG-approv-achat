package afg.achat.afgApprovAchat.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping("/admin/maintenance")
public class MaintenanceController {
    @GetMapping("")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) // renvoie 503
    public String maintenance() {
        return "error/maintenance";
    }
}
