package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.service.util.ImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/import")
public class ImportController {
    @Autowired
    ImportService importService;

    @GetMapping("/import-saisie-page")
    public String importPage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri",  request.getRequestURI());
        return "util/import-saisie";
    }

    @PostMapping("/import-data")
    public String importData(@RequestParam(name = "file") MultipartFile file, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("ko",
                    "Veuillez sélectionner un fichier");
            return "redirect:/import/import-saisie-page";
        }
        importService.importCSVFournisseur(file);
        redirectAttributes.addFlashAttribute("ok",
                "Import effectué avec succès");
        return "redirect:/import/import-saisie-page";
    }
}
