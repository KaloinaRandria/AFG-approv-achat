package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.FournisseurService;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonFilleService;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonMereService;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
@Controller
@RequestMapping("/bonlivraison")
public class BonLivraisonController {
    @Autowired
    FournisseurService fournisseurService;
    @Autowired
    DeviseService deviseService;
    @Autowired
    ArticleService articleService;
    @Autowired
    BonLivraisonMereService bonLivraisonMereService;
    @Autowired
    BonLivraisonFilleService bonLivraisonFilleService;


    @GetMapping("/list")
    public String getAllBonLivraisons(Model model) {

        return "bl/bl-liste";
    }

    @GetMapping("/add")
    public String addBonLivraisonPage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());

        model.addAttribute("fournisseurs", fournisseurService.getAllFournisseurs());
        model.addAttribute("devises", deviseService.getAllDevises());
        model.addAttribute("articles", articleService.getAllArticles());
        return "bl/bl-saisie";
    }

    @GetMapping("/save")
    public String insertBonLivraison(@RequestParam(name = "fournisseurs") String fournisseurId,
                                     @RequestParam(name = "devises") String deviseId,
                                     @RequestParam(name = "dateLivraison") String dateLivraison,
                                     @RequestParam(name = "description" , required = false) String description,
                                     @RequestParam("articleCodes[]") List<String> articleCodes,
                                     @RequestParam("quantiteDemande[]") List<String> qteDemandes,
                                     @RequestParam("quantiteRecu[]") List<String> qteRecues,
                                     @RequestParam("prixUnitaire[]") List<String> prixUnitaires,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        try {
            if (fournisseurId == null || fournisseurId.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "Veuillez sélectionner un fournisseur.");
                return "redirect:/bonlivraison/add";
            }
            if (deviseId == null || deviseId.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "Veuillez sélectionner une devise.");
                return "redirect:/bonlivraison/add";
            }
            Fournisseur fournisseur = fournisseurService.getFournisseurById(Integer.parseInt(fournisseurId))
                    .orElseThrow(() -> new IllegalArgumentException("Fournisseur introuvable"));

            Devise devise = deviseService.getDeviseById(Integer.parseInt(deviseId))
                    .orElseThrow(() -> new IllegalArgumentException("Devise introuvable"));

//            Enregistrement du bon de livraison mère
            BonLivraisonMere bonLivraisonMere = new BonLivraisonMere();
            bonLivraisonMere.setFournisseur(fournisseur);
            bonLivraisonMere.setDevise(devise);
            bonLivraisonMere.setDateReception(dateLivraison);
            bonLivraisonMere.setDescription(description != null ? description : "");

            this.bonLivraisonMereService.insertBonLivraisonMere(bonLivraisonMere);


//            Enregistrement des bons de livraison filles
            List<BonLivraisonFille> bonLivraisonFilles = new ArrayList<>();
            for (int i = 0; i < articleCodes.size(); i++) {
                BonLivraisonFille bonLivraisonFille = new BonLivraisonFille();
                bonLivraisonFille.setBonLivraisonMere(bonLivraisonMere);
                int finalI = i;
                bonLivraisonFille.setArticle(articleService.getArticleByCodeArticle(articleCodes.get(i))
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable avec le code: " + articleCodes.get(finalI))));
                bonLivraisonFille.setQuantiteDemande(qteDemandes.get(i));
                bonLivraisonFille.setQuantiteRecu(qteRecues.get(i));
                bonLivraisonFille.setPrixUnitaire(prixUnitaires.get(i));

                bonLivraisonFilles.add(bonLivraisonFille);
                this.bonLivraisonFilleService.insertBonLivraisonFilleList(bonLivraisonFille);
            }

            redirectAttributes.addFlashAttribute("ok", "Bon de livraison enregistré avec succès.");
//            redirecte mankany am page liste rehefa misy page liste
            return "redirect:/bonlivraison/add";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/bonlivraison/add";
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'enregistrement du bon de livraison.");
            return "redirect:/bonlivraison/add";
        }
    }
}
