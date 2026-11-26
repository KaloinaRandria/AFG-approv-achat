package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.service.util.CoursIndicatifService;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import afg.achat.afgApprovAchat.service.util.FormatNumber;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller
@RequestMapping("/devise")
public class DeviseController {
    /** Au-delà de ce nombre de jours sans mise à jour, un cours est signalé comme ancien. */
    static final long JOURS_COURS_ANCIEN = 7;

    @Autowired
    DeviseService deviseService;
    @Autowired
    CoursIndicatifService coursIndicatifService;

    @GetMapping("/devise-pages")
    public String goToDevisePage(Model model, HttpServletRequest request) {

        Devise[] listeDevises = deviseService.getAllDevises();
        List<Devise> etrangeres = Arrays.stream(listeDevises)
                .filter(d -> !Devise.CODE_REFERENCE.equalsIgnoreCase(d.getAcronyme()))
                .sorted(Comparator.comparing(Devise::getAcronyme, String.CASE_INSENSITIVE_ORDER))
                .toList();

        LocalDate aujourdhui = LocalDate.now();
        Map<Integer, Long> joursDepuisMaj = new HashMap<>();
        for (Devise d : etrangeres) {
            if (d.getDateMiseAJour() != null) {
                joursDepuisMaj.put(d.getId(),
                        ChronoUnit.DAYS.between(d.getDateMiseAJour().toLocalDate(), aujourdhui));
            }
        }
        long coursAnciens = etrangeres.stream()
                .filter(d -> joursDepuisMaj.getOrDefault(d.getId(), Long.MAX_VALUE) > JOURS_COURS_ANCIEN)
                .count();

        model.addAttribute("peutModifier",
                request.isUserInRole("ROLE_ADMIN") || request.isUserInRole("ROLE_MOYENS_GENERAUX"));
        model.addAttribute("devises", listeDevises);
        model.addAttribute("devisesEtrangeres", etrangeres);
        model.addAttribute("deviseReference", deviseService.getDeviseReference());
        model.addAttribute("joursDepuisMaj", joursDepuisMaj);
        model.addAttribute("joursCoursAncien", JOURS_COURS_ANCIEN);
        model.addAttribute("coursAnciens", coursAnciens);
        model.addAttribute("derniereMaj", etrangeres.stream()
                .map(Devise::getDateMiseAJour).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        if (!model.containsAttribute("devise")) {
            model.addAttribute("devise", new Devise());
        }

        return "devise/devise-saisie-liste";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @PostMapping("save-devise")
    public String insertDevise(@RequestParam(name = "acronyme") String acronyme,
                               @RequestParam(name = "designation") String designation,
                               @RequestParam(name = "coursAriary") String coursAriary,
                               RedirectAttributes redirectAttributes) {
        try {
            String code = acronyme == null ? "" : acronyme.trim().toUpperCase();
            if (!code.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("l'acronyme doit être un code de 3 lettres (ex. USD, EUR).");
            }
            if (designation == null || designation.isBlank()) {
                throw new IllegalArgumentException("la désignation est requise.");
            }
            if (deviseService.existeAcronyme(code)) {
                throw new IllegalArgumentException("la devise " + code + " existe déjà.");
            }
            double cours = lireCours(coursAriary);

            Devise devise = new Devise();
            devise.setAcronyme(code);
            devise.setDesignation(designation.trim());
            devise.setCoursAriary(String.valueOf(cours));
            devise.setDateMiseAJour(String.valueOf(LocalDateTime.now()));
            deviseService.insertDevise(devise);
            redirectAttributes.addFlashAttribute("ok", "Devise " + devise.getDesignation()
                    + " (" + devise.getAcronyme() + ") ajoutée : 1 " + code + " = "
                    + FormatNumber.cours(cours) + " MGA" + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'ajout de la devise : " + e.getMessage());
        }
        return "redirect:/devise/devise-pages";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @PostMapping("/modifier-cours")
    public String modifierCoursDevise(@RequestParam(name = "idDevise") String idDevise,
                                      @RequestParam(name = "coursAriary") String coursAriary,
                                      RedirectAttributes redirectAttributes) {
        try {
            if (idDevise == null || idDevise.isBlank()) {
                throw new IllegalArgumentException("l'identifiant de la devise est requis.");
            }
            double cours = lireCours(coursAriary);
            Devise deviseModifier = deviseService.modifierDevise(Integer.parseInt(idDevise.trim()), String.valueOf(cours));
            redirectAttributes.addFlashAttribute("ok", "Cours " + deviseModifier.getAcronyme() + " mis à jour : 1 "
                    + deviseModifier.getAcronyme() + " = "
                    + FormatNumber.cours(deviseModifier.getCoursAriary()) + " MGA" + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de la modification du cours : " + e.getMessage());
        }
        return "redirect:/devise/devise-pages";
    }

    /**
     * Cours indicatif d'une devise lu sur une API publique, pour pré-remplir la saisie.
     * Rien n'est enregistré : l'utilisateur valide le cours lui-même.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @GetMapping("/cours-indicatif/{acronyme}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> coursIndicatif(@PathVariable String acronyme) {
        Map<String, Object> corps = new LinkedHashMap<>();
        try {
            CoursIndicatifService.CoursIndicatif c = coursIndicatifService.coursIndicatif(acronyme);
            corps.put("devise", c.devise());
            corps.put("coursAriary", c.coursAriary());
            corps.put("dateSource", c.dateSource() == null ? null
                    : c.dateSource().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            corps.put("source", c.source());
            return ResponseEntity.ok(corps);
        } catch (IllegalArgumentException e) {
            corps.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corps);
        } catch (Exception e) {
            corps.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(corps);
        }
    }

    /** Cours saisi (virgule ou point décimal, espaces tolérés), strictement positif. */
    static double lireCours(String coursAriary) {
        if (coursAriary == null || coursAriary.isBlank()) {
            throw new IllegalArgumentException("le cours en Ariary est requis.");
        }
        double cours;
        try {
            cours = Double.parseDouble(coursAriary.replaceAll("[\\s\\u00A0\\u202F]", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("le cours en Ariary est invalide.");
        }
        if (!(cours > 0) || Double.isInfinite(cours)) {
            throw new IllegalArgumentException("le cours en Ariary doit être supérieur à 0.");
        }
        return cours;
    }
}
