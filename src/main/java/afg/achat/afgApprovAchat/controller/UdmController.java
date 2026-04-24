package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.service.util.UdmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/udm")
public class UdmController {
    @Autowired
    UdmService udmService;

    @GetMapping("/udm-pages")
    public String goToUdmPage(Model model) {
        Udm[] listeUdms = udmService.getAllUdms();
        model.addAttribute("udms", listeUdms);
        model.addAttribute("udm", new Udm());

        return "udm/udm-saisie-liste";
    }
    
}
