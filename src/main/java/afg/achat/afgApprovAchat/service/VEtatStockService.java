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

    public Page<VEtatStock> searchEtatStockMulti(
            String code,
            String designation,
            String udm,
            String etat,
            Pageable pageable
    ) {
        String c = (code == null) ? "" : code.trim();
        String d = (designation == null) ? "" : designation.trim();
        String u = (udm == null) ? "" : udm.trim();
        String e = (etat == null) ? "" : etat.trim().toUpperCase(); // RUPTURE/SEUIL/NORMAL

        return vEtatStockRepo.searchMulti(c, d, u, e, pageable);
    }
}
