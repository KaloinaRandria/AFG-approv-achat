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
        return "util/import-saisie";
    }

    @PostMapping("/import-data")
    public String importData(
//            @RequestParam(name = "file1") MultipartFile file1, // Articles
//            @RequestParam(name = "file2") MultipartFile file2, // Familles
//            @RequestParam(name = "file3") MultipartFile file3, // Fournisseurs
//            @RequestParam(name = "file4") MultipartFile file4, // Achats / BL
            @RequestParam(name = "file5") MultipartFile file5, // Achats / BL

            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        // 1) référentiels
//        importService.importCSVFamille(file2);
//        importService.importCSVFournisseur(file3);
//        importService.importCSVArticle(file1);

        importService.importCSVPoste(file5);

        // 2) achats -> BL + stock
//        importService.importCSVAchat(file4); // <= c’est ici qu’on crée BL_MERE + BL_FILLE + STOCK_MERE + STOCK_FILLE
//        importService.importCSVSortieStock(file4);
        redirectAttributes.addFlashAttribute("ok", "Import effectué avec succès");
        return "redirect:/import/import-saisie-page";
    }
}
