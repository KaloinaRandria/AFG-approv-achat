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
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
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
    @Autowired
    IdGenerator idGenerator;

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
                                @RequestParam(name = "description") String description,
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
            demandeMere.setId(idGenerator);
            demandeMere.setDateDemande(dateDemande);
            demandeMere.setAdresse(adresse1);
            demandeMere.setDemandeur(utilisateur);
            demandeMere.setDescription(description);

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
    public String listDemandePage(Model model,
                                  HttpServletRequest request,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(defaultValue = "dateDemande") String sort,
                                  @RequestParam(defaultValue = "desc") String dir,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        var auth = SecurityContextHolder.getContext().getAuthentication();

        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdminOrMG = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_MOYENS_GENERAUX")
        );

        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(utilisateur.getId());

// ✅ admin/mg voient demandeur, et n+1 aussi (visibleIds > 1)
        boolean showDemandeurColumn = isAdminOrMG || (visibleIds.size() > 1);
        model.addAttribute("showDemandeurColumn", showDemandeurColumn);

// ensuite tu choisis la recherche:
// - admin/mg => tout
// - sinon => visibleIds (moi + enfants)
        var demandesMeres = isAdminOrMG
                ? demandeMereService.searchDemandes(q, dateFrom, dateTo, page, size, sort, dir)
                : demandeMereService.searchDemandesVisibleParUtilisateur(q, dateFrom, dateTo, visibleIds, page, size, sort, dir);

        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("demandesMeres", demandesMeres);

        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());

        return "demande/demande-liste";
    }

}
