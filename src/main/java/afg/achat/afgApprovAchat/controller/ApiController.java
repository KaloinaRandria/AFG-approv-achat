package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
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

}
