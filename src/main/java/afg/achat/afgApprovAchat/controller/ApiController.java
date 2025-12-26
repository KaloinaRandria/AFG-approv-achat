package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.ArticleLivraisonDTO;
import afg.achat.afgApprovAchat.model.BonLivraisonDetailDTO;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonFilleService;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonMereService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:8080")
@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    ArticleService articleService;
    @Autowired
    ArticleRepo articleRepo;
    @Autowired
    BonLivraisonMereService bonLivraisonMereService;
    @Autowired
    BonLivraisonFilleService bonLivraisonFilleService;

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
    public BonLivraisonDetailDTO getBonLivraisonDetails(@PathVariable int id) {
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


}
