package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class DemandeMereService {
    @Autowired
    DemandeMereRepo demandeMereRepo;

    public DemandeMere[] getAllDemandesMeres() {
        return demandeMereRepo.findAll().toArray(new DemandeMere[0]);
    }

    public void saveDemandeMere(DemandeMere demandeMere) {
        demandeMereRepo.save(demandeMere);
    }

    public Page<DemandeMere> searchDemandes(String q,
                                            LocalDate dateFrom,
                                            LocalDate dateTo,
                                            int page,
                                            int size,
                                            String sort,
                                            String dir) {

        // Tri
        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        // Bornes de date NON NULL (évite bug postgres)
        LocalDateTime from = (dateFrom == null)
                ? LocalDate.of(1900, 1, 1).atStartOfDay()
                : dateFrom.atStartOfDay();

        LocalDateTime to = (dateTo == null)
                ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59)
                : dateTo.atTime(23, 59, 59);

        String keyword = (q == null) ? "" : q.trim();

        return demandeMereRepo.search(keyword, from, to, pageable);
    }
    public Optional<DemandeMere> getDemandeMereById(String id) {
        return demandeMereRepo.findById(id);
    }

    public Page<DemandeMere> searchDemandesByDemandeur(String q,
                                                       LocalDate dateFrom,
                                                       LocalDate dateTo,
                                                       String demandeurId,
                                                       int page,
                                                       int size,
                                                       String sort,
                                                       String dir) {

        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        LocalDateTime from = (dateFrom == null)
                ? LocalDate.of(1900, 1, 1).atStartOfDay()
                : dateFrom.atStartOfDay();

        LocalDateTime to = (dateTo == null)
                ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59)
                : dateTo.atTime(23, 59, 59);

        String keyword = (q == null) ? "" : q.trim();

        return demandeMereRepo.searchByDemandeur(keyword, from, to, demandeurId, pageable);
    }

}
