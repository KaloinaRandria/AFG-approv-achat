package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.repository.stock.HistoriqueStockRepository;
import afg.achat.afgApprovAchat.service.stock.HistoriqueMouvementStockProjection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stock")
public class HistoriqueStockController {

    private final HistoriqueStockRepository repo;

    public HistoriqueStockController(HistoriqueStockRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/historique")
    public List<HistoriqueMouvementStockProjection> historique(@RequestParam("idArticle") int idArticle) {
        return repo.findByIdArticle(idArticle);
    }
}

