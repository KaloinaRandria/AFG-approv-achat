package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ArticleService {
    @Autowired
    ArticleRepo articleRepo;

    public Article[] getAllArticles() {
        return articleRepo.findAll().toArray(new Article[0]);
    }

    public Article getArticleByCodeArticle(String codeArticle) {
        return articleRepo.findArticleByCodeArticle(codeArticle);
    }
}
