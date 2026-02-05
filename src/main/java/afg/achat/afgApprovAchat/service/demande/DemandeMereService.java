package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DemandeMereService {
    @Autowired
    DemandeMereRepo demandeMereRepo;
    @Autowired
    DemandeFilleRepo demandeFilleRepo;

    public DemandeMere[] getAllDemandesMeres() {
        return demandeMereRepo.findAll().toArray(new DemandeMere[0]);
    }

    public void saveDemandeMere(DemandeMere demandeMere) {
        demandeMereRepo.save(demandeMere);
    }

    public Page<DemandeMere> searchDemandes(String num,
                                            String demandeur,
                                            String type,
                                            Integer statut,
                                            LocalDate dateFrom,
                                            LocalDate dateTo,
                                            int page,
                                            int size,
                                            String sort,
                                            String dir) {

        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        LocalDateTime from = (dateFrom == null) ? LocalDate.of(1900, 1, 1).atStartOfDay() : dateFrom.atStartOfDay();
        LocalDateTime to = (dateTo == null) ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59) : dateTo.atTime(23, 59, 59);

        String n = (num == null) ? "" : num.trim();
        String d = (demandeur == null) ? "" : demandeur.trim();
        String t = (type == null) ? "" : type.trim();

        // null = tous
        Integer st = (statut == null || statut == 0) ? null : statut;

        return demandeMereRepo.searchMulti(n, d, t, st, from, to, pageable);
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

    public Page<DemandeMere> searchDemandesVisibleParUtilisateur(String num,
                                                                 String demandeur,
                                                                 String type,
                                                                 Integer statut,
                                                                 LocalDate dateFrom,
                                                                 LocalDate dateTo,
                                                                 List<Integer> demandeurIds,
                                                                 int page,
                                                                 int size,
                                                                 String sort,
                                                                 String dir) {

        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        LocalDateTime from = (dateFrom == null) ? LocalDate.of(1900, 1, 1).atStartOfDay() : dateFrom.atStartOfDay();
        LocalDateTime to = (dateTo == null) ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59) : dateTo.atTime(23, 59, 59);

        String n = (num == null) ? "" : num.trim();
        String d = (demandeur == null) ? "" : demandeur.trim();
        String t = (type == null) ? "" : type.trim();

        Integer st = (statut == null || statut == 0) ? null : statut;

        return demandeMereRepo.searchMultiByDemandeurIds(n, d, t, st, from, to, demandeurIds, pageable);
    }

    @Transactional
    public void appliquerDecisionGlobale(DemandeMere demande, int nouveauStatut) {

        // 1) update demande mère
        demande.setStatut(nouveauStatut);
        demandeMereRepo.save(demande);

        // 2) update toutes les filles
        List<DemandeFille> filles = demandeFilleRepo.findDemandeFilleByDemandeMere(demande);
        for (DemandeFille f : filles) {
            f.setStatut(nouveauStatut);
        }
        demandeFilleRepo.saveAll(filles);
    }

    public Page<DemandeMere> searchDemandes(String num,
                                            String demandeur,
                                            String type,
                                            List<Integer> statuts,
                                            LocalDate dateFrom,
                                            LocalDate dateTo,
                                            int page,
                                            int size,
                                            String sort,
                                            String dir) {

        // ✅ tri / pagination
        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        // ✅ dates (borne large si null)
        LocalDateTime from = (dateFrom == null)
                ? LocalDate.of(1900, 1, 1).atStartOfDay()
                : dateFrom.atStartOfDay();

        LocalDateTime to = (dateTo == null)
                ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59)
                : dateTo.atTime(23, 59, 59);

        // ✅ normalisation des champs texte
        String n = (num == null) ? "" : num.trim();
        String d = (demandeur == null) ? "" : demandeur.trim();
        String t = (type == null) ? "" : type.trim();

        // ✅ statuts : null ou vide => pas de filtre
        List<Integer> st = (statuts == null || statuts.isEmpty()) ? null : statuts;

        return demandeMereRepo.searchMultiWithStatuts(n, d, t, st, from, to, pageable);
    }




}
