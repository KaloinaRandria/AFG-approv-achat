package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.*;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonFilleService;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonMereService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:8080")
@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    ArticleRepo articleRepo;
    @Autowired
    BonLivraisonMereService bonLivraisonMereService;
    @Autowired
    BonLivraisonFilleService bonLivraisonFilleService;
    @Autowired
    DemandeMereService demandeMereService;
    @Autowired
    DemandeFilleService demandeFilleService;

    @GetMapping("/articles/search")
    @ResponseBody
    public List<Map<String, String>> searchArticles(@RequestParam String keyword) {
        return articleRepo
                .findByCodeArticleContainingIgnoreCaseOrDesignationContainingIgnoreCase(keyword, keyword)
                .stream()
                .map(a -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("code", a.getCodeArticle());
                    map.put("designation", a.getDesignation());
                    return map;
                })
                .toList();
    }

    @GetMapping("/bonlivraison/{id}/details")
    public BonLivraisonDetailDTO getBonLivraisonDetails(@PathVariable String id) {
        BonLivraisonMere bonLivraisonMere = this.bonLivraisonMereService.getBonLivraisonMereById(id)
                .orElseThrow(() -> new RuntimeException("Bon de livraison non trouvé avec l'ID: " + id));

        BonLivraisonDetailDTO dto = new BonLivraisonDetailDTO();
        dto.setId(bonLivraisonMere.getId());
        dto.setFournisseur(bonLivraisonMere.getFournisseur().getNom());
        dto.setDate(bonLivraisonMere.getDateReception().toString());
        dto.setDevise(bonLivraisonMere.getDevise().getDesignation());

        List<BonLivraisonFille> lignes =
                bonLivraisonFilleService.getBonLivraisonFillesByMereId(id);
        List<ArticleLivraisonDTO> articles = lignes.stream().map(ligne -> {
            ArticleLivraisonDTO a = new ArticleLivraisonDTO();
            a.setDesignation(ligne.getArticle().getDesignation());
            a.setQuantite(ligne.getQuantiteRecu());
            return a;
        }).toList();

        dto.setArticles(articles);

        return dto;
    }

    @GetMapping("/demande/{id}/details")
    public DemandeDetailDTO getDemandeDetails(@PathVariable String id) {

        DemandeMere demandeMere = this.demandeMereService.getDemandeMereById(id)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée avec l'ID: " + id));

        DemandeDetailDTO dto = new DemandeDetailDTO();

        dto.setId(demandeMere.getId());

        // Demandeur
        String nom = demandeMere.getDemandeur() != null ? demandeMere.getDemandeur().getNom() : "";
        String prenom = demandeMere.getDemandeur() != null ? demandeMere.getDemandeur().getPrenom() : "";


        // Date
        dto.setDateDemande(demandeMere.getDateDemande());

        // Type
        dto.setTypeDemande(
                demandeMere.getNatureDemande() != null ? demandeMere.getNatureDemande().toString() : "-"
        );

        // ✅ Statut (libellé)
        Map<Integer, String> statutLabels = Map.of(
                StatutDemande.CREE, "CREE",
                StatutDemande.VALIDATION_N1, "EN_VALIDATION",
                StatutDemande.VALIDATION_N2, "EN_VALIDATION",
                StatutDemande.VALIDATION_N3, "EN_VALIDATION",
                StatutDemande.VALIDE, "VALIDE",
                StatutDemande.REFUSE, "REFUSE"
        );
        dto.setStatutDemande(statutLabels.getOrDefault(demandeMere.getStatut(), "INCONNU"));

        // Articles
        List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demandeMere);

        List<ArticleDemandeDTO> articles = lignes.stream().map(ligne -> {
            ArticleDemandeDTO a = new ArticleDemandeDTO();
            a.setDesignation(ligne.getArticle() != null ? ligne.getArticle().getDesignation() : "-");
            a.setQuantite(ligne.getQuantite() != 0.0 ? ligne.getQuantite() : 0);
            return a;
        }).toList();

        dto.setArticles(articles);

        return dto;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TypeDemandeUpdateRequest {
        public String typeDemande; // "OPEX" ou "CAPEX" ou ""/null
    }

    @PutMapping("/demande/{id}/type-demande")
    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @Transactional
    public ResponseEntity<?> updateTypeDemande(@PathVariable String id,
                                               @RequestBody TypeDemandeUpdateRequest req) {

        DemandeMere demande = demandeMereService.getDemandeMereById(id)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée: " + id));

        String val = req.getTypeDemande();

        if (val == null || val.trim().isEmpty()) {
            demande.setNatureDemande(null);
        } else {
            if (demande.getStatut() != 1) {
                return ResponseEntity.badRequest().body("Impossible de modifier le type : demande déjà en cours de validation.");
            }
            demande.setNatureDemande(DemandeMere.NatureDemande.valueOf(val.trim().toUpperCase()));
        }

        demandeMereService.saveDemandeMere(demande); // ou demandeMereRepo.save(demande)

        return ResponseEntity.ok().build();
    }

}
