package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.ArticleHistorique;
import afg.achat.afgApprovAchat.repository.util.ArticleHistoriqueRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ArticleHistoriqueService {
    @Autowired
    ArticleHistoriqueRepo articleHistoriqueRepo;

    public void saveArticleHistorique(ArticleHistorique articleHistorique) {
        articleHistoriqueRepo.save(articleHistorique);
    }
}
