package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.Adresse;
import afg.achat.afgApprovAchat.model.util.Departement;
import afg.achat.afgApprovAchat.service.util.AdresseService;
import afg.achat.afgApprovAchat.service.util.DepartementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/demande")
public class DemandeController {
    @Autowired
    AdresseService adresseService;
    @Autowired
    DepartementService departementService;
    @GetMapping("/add")
    public String addDemandePage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("natures", DemandeMere.NatureDemande.values());
        Adresse[] adresses = adresseService.getAllAdresses();
        model.addAttribute("adresses", adresses);
        Departement[] departements = departementService.getAllDepartements();
        model.addAttribute("departements", departements);

        return "demande/demande-saisie";
    }
}
