package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonMereRepo;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Service
public class BonLivraisonMereService {

    private final BonLivraisonMereRepo bonLivraisonMereRepo;

    public BonLivraisonMereService(BonLivraisonMereRepo bonLivraisonMereRepo) {
        this.bonLivraisonMereRepo = bonLivraisonMereRepo;
    }

    // ✅ pageable (avec tri)
    public Page<BonLivraisonMere> getBonLivraisonMeresPage(int page, int size, String sort, String dir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));
        return bonLivraisonMereRepo.findAll(pageable);
    }

    /** Origine d'un bon de livraison, pour le filtre de la liste. */
    public enum Origine {
        /** Réception d'un bon de commande. */
        BC,
        /** Saisie directe, sans bon de commande. */
        HORS_BC
    }

    /** Critères de la liste des bons de livraison ; les champs vides ou {@code null} sont ignorés. */
    public record Recherche(String numero, String fournisseur, String bonCommande, String article,
                            Integer deviseId, Origine origine, LocalDate du, LocalDate au) {
    }

    /** Recherche paginée des bons de livraison, du plus récent au plus ancien. */
    public Page<BonLivraisonMere> rechercher(Recherche r, Pageable pageable) {
        return bonLivraisonMereRepo.rechercher(
                r.du() == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : r.du().atStartOfDay(),
                r.au() == null ? LocalDateTime.of(2999, 12, 31, 23, 59, 59) : r.au().atTime(LocalTime.MAX),
                texte(r.numero()), texte(r.fournisseur()), texte(r.bonCommande()), texte(r.article()),
                r.deviseId() == null ? 0 : r.deviseId(),
                r.origine() == null ? "" : r.origine().name(),
                pageable);
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur.trim();
    }


    public void insertBonLivraisonMere(BonLivraisonMere bonLivraisonMere) {
        this.bonLivraisonMereRepo.save(bonLivraisonMere);
    }

    public Optional<BonLivraisonMere> getBonLivraisonMereById(String id) {
        return this.bonLivraisonMereRepo.findById(id);
    }

    public BonLivraisonMere getBonLivraisonMereByIdFacture(String idFacture) {
        return bonLivraisonMereRepo.findByIdFacture(idFacture);
    }

    public boolean existsByIdFacture(String idFacture) {
        return bonLivraisonMereRepo.existsByIdFacture(idFacture);
    }
}
