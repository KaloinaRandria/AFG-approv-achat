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

    public Page<BonLivraisonMere> searchBonLivraisonMeres(String q,
                                                          LocalDate dateFrom,
                                                          LocalDate dateTo,
                                                          int page, int size, String sort, String dir) {

        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        String keyword = (q == null) ? "" : q.trim();

        LocalDateTime from = (dateFrom != null) ? dateFrom.atStartOfDay() : null;
        LocalDateTime to   = (dateTo != null) ? dateTo.atTime(LocalTime.MAX) : null;

        return bonLivraisonMereRepo.search(keyword, from, to, pageable);
    }


    public void insertBonLivraisonMere(BonLivraisonMere bonLivraisonMere) {
        this.bonLivraisonMereRepo.save(bonLivraisonMere);
    }

    public Optional<BonLivraisonMere> getBonLivraisonMereById(String id) {
        return this.bonLivraisonMereRepo.findById(id);
    }
}
