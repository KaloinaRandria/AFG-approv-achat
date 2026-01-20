package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.model.stock.StockAlerte;
import afg.achat.afgApprovAchat.repository.VEtatStockRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StockAlerteService {

    @Autowired
    private VEtatStockRepo vEtatStockRepo;

    // ✅ Badge
    public int getAlertesCount() {
        long count = vEtatStockRepo.countAlertes();
        return (count > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count;
    }

    // ✅ Modal notifications paginé
    public Page<StockAlerte> getAlertesPage(Pageable pageable) {
        Page<VEtatStock> page = vEtatStockRepo.findAlertesPage(pageable);

        List<StockAlerte> content = page.getContent().stream()
                .map(this::toAlerte)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    // ✅ Alertes uniquement pour les articles affichés (page courante)
    public Map<String, StockAlerte> getAlertesMapForCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyMap();

        List<VEtatStock> etats = vEtatStockRepo.findAlertesForCodes(codes);
        Map<String, StockAlerte> map = new HashMap<>();

        for (VEtatStock v : etats) {
            StockAlerte a = toAlerte(v);
            if (a != null) map.put(a.getCodeArticle(), a);
        }
        return map;
    }

    private StockAlerte toAlerte(VEtatStock etat) {
        double stock = parseDoubleSafe(etat.getStockDisponible());
        double seuil = parseDoubleSafe(etat.getSeuilMin());

        String type = null;
        if (stock <= 0) type = "RUPTURE";
        else if (stock <= seuil) type = "SEUIL";
        else return null;

        return new StockAlerte(
                etat.getCodeArticle(),
                etat.getDesignation(),
                stock,
                seuil,
                type,
                etat.getUdm()
        );
    }

    private double parseDoubleSafe(String s) {
        try {
            if (s == null) return 0;
            String v = s.replace(",", ".").trim();
            if (v.isEmpty()) return 0;
            return Double.parseDouble(v);
        } catch (Exception e) {
            return 0;
        }
    }
}
