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

    public Page<BonLivraisonMere> searchBonLivraisonMeres(String num,
                                                          String fournisseur,
                                                          String devise,
                                                          LocalDate dateFrom,
                                                          LocalDate dateTo,
                                                          int page, int size, String sort, String dir) {

        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        String n = (num == null) ? "" : num.trim();
        String f = (fournisseur == null) ? "" : fournisseur.trim();
        String d = (devise == null) ? "" : devise.trim();

        // ✅ bornes non-null (évite soucis + logique claire)
        LocalDateTime from = (dateFrom == null)
                ? LocalDate.of(1900, 1, 1).atStartOfDay()
                : dateFrom.atStartOfDay();

        LocalDateTime to = (dateTo == null)
                ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59)
                : dateTo.atTime(23, 59, 59);

        return bonLivraisonMereRepo.searchMulti(n, f, d, from, to, pageable);
    }


    public void insertBonLivraisonMere(BonLivraisonMere bonLivraisonMere) {
        this.bonLivraisonMereRepo.save(bonLivraisonMere);
    }

    public Optional<BonLivraisonMere> getBonLivraisonMereById(String id) {
        return this.bonLivraisonMereRepo.findById(id);
    }
}
