package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.Adresse;
import afg.achat.afgApprovAchat.model.util.Departement;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.util.AdresseService;
import afg.achat.afgApprovAchat.service.util.DepartementService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/demande")
public class DemandeController {
    @Autowired
    AdresseService adresseService;
    @Autowired
    DepartementService departementService;
    @Autowired
    DemandeMereService demandeMereService;
    @Autowired
    DemandeFilleService demandeFilleService;
    @Autowired
    ArticleService articleService;
    @Autowired
    UtilisateurService utilisateurService;

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

    @PostMapping("/save")
    public String insertDemande(@RequestParam(name = "dateDemande") String dateDemande,
                                @RequestParam(name = "adresse") String adresse,
                                @RequestParam(name = "departement") String departement,
                                @RequestParam(name = "articleCodes[]") List<String> articleCodes,
                                @RequestParam(name = "quantite[]") List<String> quantite,
                                RedirectAttributes redirectAttributes) {

        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(user.getMail());
         
        try {
            if (dateDemande == null || dateDemande.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "La date de la demande est obligatoire.");
                return "redirect:/demande/add";
            }
            if (adresse == null || adresse.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "L'adresse est obligatoire.");
                return "redirect:/demande/add";
            }
            if (departement == null || departement.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "Le département est obligatoire.");
                return "redirect:/demande/add";
            }
            Adresse adresse1 = adresseService.getAdresseById(Integer.parseInt(adresse))
                    .orElseThrow(() -> new IllegalArgumentException("Adresse introuvable"));
            Departement departement1 = departementService.getDepartementById(Integer.parseInt(departement))
                    .orElseThrow(() -> new IllegalArgumentException("Département introuvable"));

            DemandeMere demandeMere = new DemandeMere();
            demandeMere.setDateDemande(dateDemande);
            demandeMere.setAdresse(adresse1);
            demandeMere.setDemandeur(utilisateur);

            this.demandeMereService.saveDemandeMere(demandeMere);

            List<DemandeFille> demandeFilles = new ArrayList<>();
            for (int i = 0; i < articleCodes.size(); i++) {
                DemandeFille demandeFille = new DemandeFille();
                demandeFille.setDemandeMere(demandeMere);
                int finalI = i;
                demandeFille.setArticle(articleService.getArticleByCodeArticle(articleCodes.get(i))
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable : " + articleCodes.get(finalI))));
                demandeFille.setQuantite(quantite.get(i));
                demandeFilles.add(demandeFille);

                this.demandeFilleService.saveDemandeFille(demandeFille);
            }

            if (articleCodes.isEmpty()) {
                redirectAttributes.addFlashAttribute(
                        "ko",
                        "Impossible de valider la demande sans aucune ligne d’article."
                );
                return "redirect:/demande/add";
            }

            boolean hasValideLine = false;
            for (int i = 0; i < articleCodes.size(); i++) {
                if (quantite.get(i) != null && !quantite.get(i).isEmpty()) {
                    try {
                        double qte = Double.parseDouble(quantite.get(i));
                        if (qte > 0) {
                            hasValideLine = true;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (!hasValideLine) {
                redirectAttributes.addFlashAttribute(
                        "ko",
                        "Veuillez saisir au moins une ligne avec une quantité reçue valide."
                );
                return "redirect:/demande/add";
            }

            redirectAttributes.addFlashAttribute("ok", "Demande enregistrée avec succès.");
            return "redirect:/demande/list";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/demande/add";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'enregistrement de la demande : " + e.getMessage());
            return "redirect:/demande/add";
        }

    }

    @GetMapping("/list")
    public String listDemandePage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());

        DemandeMere[] demandesMeres = demandeMereService.getAllDemandesMeres();
        model.addAttribute("demandesMeres", demandesMeres);
        return "demande/demande-liste";
    }
}
