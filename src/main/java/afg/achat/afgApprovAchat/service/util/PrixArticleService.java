package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.PrixArticle;
import afg.achat.afgApprovAchat.repository.util.PrixArticleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PrixArticleService {
    @Autowired
    PrixArticleRepo prixArticleRepo;
    public void insert(PrixArticle prixArticle) {
        this.prixArticleRepo.save(prixArticle);
    }
    
    public Optional<PrixArticle> getDernierPrixByArticle(String codeArticle) {
        return this.prixArticleRepo.findDernierPrixByArticle(codeArticle);
    }
}
