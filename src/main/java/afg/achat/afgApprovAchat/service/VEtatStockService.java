package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.repository.VEtatStockRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class VEtatStockService {
    @Autowired
    VEtatStockRepo vEtatStockRepo;

    // ✅ Pageable
    public Page<VEtatStock> getEtatStocksPage(String q, Pageable pageable) {
        if (q == null || q.trim().isEmpty()) {
            return vEtatStockRepo.findAll(pageable);
        }
        String keyword = q.trim();
        return vEtatStockRepo
                .findByCodeArticleContainingIgnoreCaseOrDesignationContainingIgnoreCase(keyword, keyword, pageable);
    }

    public VEtatStock getEtatStockByCode(String codeArticle) {
        return vEtatStockRepo.findByCodeArticle(codeArticle);
    }
}
