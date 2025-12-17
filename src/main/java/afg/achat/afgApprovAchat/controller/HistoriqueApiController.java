package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.util.ArticleHistorique;
import afg.achat.afgApprovAchat.model.util.DeviseHistorique;
import afg.achat.afgApprovAchat.service.util.ArticleHistoriqueService;
import afg.achat.afgApprovAchat.service.util.DeviseHistoriqueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:8080")
@RestController
@RequestMapping("/api/historique")
public class HistoriqueApiController {

    private final ArticleHistoriqueService articleHistoriqueService;

    private final DeviseHistoriqueService deviseHistoriqueService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public HistoriqueApiController(ArticleHistoriqueService articleHistoriqueService, DeviseHistoriqueService deviseHistoriqueService) {
        this.articleHistoriqueService = articleHistoriqueService;
        this.deviseHistoriqueService = deviseHistoriqueService;
    }

    @GetMapping("/article/{codeArticle}")
    public ResponseEntity<?> getHistoriqueArticle(
            @PathVariable String codeArticle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            ArticleHistorique[] historiques = articleHistoriqueService.getArticleHistoriqueByCodeArticle(codeArticle);

            // Trier par date décroissante
            List<ArticleHistorique> sortedList = Arrays.stream(historiques)
                    .sorted((h1, h2) -> h2.getDateModification().compareTo(h1.getDateModification()))
                    .collect(Collectors.toList());

            // Pagination
            int total = sortedList.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);

            List<ArticleHistorique> pageContent = sortedList.subList(start, end);

            // Formater la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("data", pageContent.stream()
                    .map(this::formatHistoriqueArticle)
                    .collect(Collectors.toList()));
            response.put("pagination", Map.of(
                    "currentPage", page,
                    "totalPages", (int) Math.ceil((double) total / size),
                    "totalElements", total,
                    "pageSize", size
            ));
            response.put("article", codeArticle);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Erreur lors de la récupération de l'historique");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/devise/{deviseId}")
    public ResponseEntity<?> getHistoriqueDevise(
            @PathVariable String deviseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            int idDevise = Integer.parseInt(deviseId);
            DeviseHistorique[] historiques = deviseHistoriqueService.getDeviseHistoriqueByIdDevise(idDevise);

            List<DeviseHistorique> sortedList = Arrays.stream(historiques)
                    .sorted((h1, h2) -> h2.getDateModification().compareTo(h1.getDateModification()))
                    .collect(Collectors.toList());

            // Pagination
            int total = sortedList.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);

            List<DeviseHistorique> pageContent = sortedList.subList(start, end);

            // Formater la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("data", pageContent.stream()
                    .map(this::formatHistoriqueDevise)
                    .collect(Collectors.toList()));
            response.put("pagination", Map.of(
                    "currentPage", page,
                    "totalPages", (int) Math.ceil((double) total / size),
                    "totalElements", total,
                    "pageSize", size
            ));
            response.put("deviseId", deviseId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Erreur lors de la récupération de l'historique");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }

    }

    private Map<String, Object> formatHistoriqueArticle(ArticleHistorique historique) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("id", historique.getId());
        formatted.put("date", historique.getDateModification().format(dateFormatter));
        formatted.put("modifiePar", historique.getModifiePar());
        formatted.put("champ", historique.getChampModifie());
        formatted.put("ancienneValeur", historique.getAncienneValeur());
        formatted.put("nouvelleValeur", historique.getNouvelleValeur());
        formatted.put("codeArticle", historique.getCodeArticle());
        return formatted;
    }

    private Map<String, Object> formatHistoriqueDevise(DeviseHistorique historique) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("id", historique.getId());
        formatted.put("date", historique.getDateModification().format(dateFormatter));
        formatted.put("modifiePar", historique.getModifiePar());
        formatted.put("ancienCoursAriary", historique.getAncienCoursAriary());
        formatted.put("nouvelleCoursAriary", historique.getNouvelleCoursAriary());
        formatted.put("deviseId", historique.getDevise().getId());
        return formatted;
    }
}