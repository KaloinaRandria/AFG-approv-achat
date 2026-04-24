package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.service.util.UdmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/udm")
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
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


    @PostMapping("/save-udm")
    public String insertUdm(@RequestParam(name = "acronyme") String acronyme,
                            @RequestParam(name = "description") String description,
                            RedirectAttributes redirectAttributes) {

        Udm udm = new Udm();

        try {
            //Nettoyage (bonne pratique)
            acronyme = acronyme.trim().toUpperCase();
            description = description.trim();

            udm.setAcronyme(acronyme);
            udm.setDescription(description);

            udmService.insertUdm(udm);

            redirectAttributes.addFlashAttribute("ok",
                    "Unité de mesure ajoutée avec succès : "
                            + description + " (" + acronyme + ")");

        } catch (IllegalArgumentException e) {

            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de l'ajout de l'unité : " + e.getMessage());
            redirectAttributes.addFlashAttribute("udm", udm);

            return "redirect:/udm/udm-pages";

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de l'ajout de l'unité : " + e.getMessage());
            redirectAttributes.addFlashAttribute("udm", udm);

            return "redirect:/udm/udm-pages";
        }

        return "redirect:/udm/udm-pages";
    }
}
