package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
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
    @Autowired
    IdGenerator idGenerator;
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
                                            String priorite,
                                            String motif,
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
        String p = (priorite == null) ? "" : priorite.trim();
        String m = (motif == null) ? "" : motif.trim();

        return demandeMereRepo.searchMulti(n, d, t, st ,p,m ,from, to, pageable);
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
                                                                 String priorite,
                                                                 String motif,
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
        String p = (priorite == null) ? "" : priorite.trim();
        String m = (motif == null) ? "" : motif.trim();

        return demandeMereRepo.searchMultiByDemandeurIds(n, d, t, st, p,m, from, to, demandeurIds, pageable);
    }

    @Transactional
    public void appliquerDecisionGlobale(DemandeMere demande, int nouveauStatut) {

        // 1) update demande mère
        demande.setStatut(nouveauStatut);
        demandeMereRepo.save(demande);

        // 2) update uniquement les lignes NON refusées
        List<DemandeFille> filles = demandeFilleRepo.findDemandeFilleByDemandeMere(demande);

        for (DemandeFille f : filles) {
            if (f.getStatut() != StatutDemande.REFUSE) {
                f.setStatut(nouveauStatut);
            }
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

        // tri / pagination
        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        // dates (borne large si null)
        LocalDateTime from = (dateFrom == null)
                ? LocalDate.of(1900, 1, 1).atStartOfDay()
                : dateFrom.atStartOfDay();

        LocalDateTime to = (dateTo == null)
                ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59)
                : dateTo.atTime(23, 59, 59);

        // normalisation des champs texte
        String n = (num == null) ? "" : num.trim();
        String d = (demandeur == null) ? "" : demandeur.trim();
        String t = (type == null) ? "" : type.trim();

        // statuts : null ou vide => pas de filtre
        List<Integer> st = (statuts == null || statuts.isEmpty()) ? null : statuts;

        return demandeMereRepo.searchMultiWithStatuts(n, d, t, st, from, to, pageable);
    }


    public void saveIfNotExists(DemandeMere dm) {
        demandeMereRepo.existsById(dm.getId());
    }

    @Transactional
    public DemandeMere getOrCreateByCodeProvisoire(String refDemande, LocalDateTime dt) {
        return demandeMereRepo.findByCodeProvisoire(refDemande)
                .orElseGet(() -> {
                    DemandeMere dm = new DemandeMere();
                    dm.setId(idGenerator);           // SI ton setId(IdGenerator) existe
                    dm.setCodeProvisoire(refDemande);
                    dm.setDateDemande(String.valueOf(dt));           // ou String si ton champ est String
                    return demandeMereRepo.save(dm);
                });
    }

    @Transactional
    public void recalculerTotal(DemandeMere demande) {

        double total = demandeFilleRepo
                .calculerTotal(demande, StatutDemande.REFUSE);

        demande.setTotalEstime(total);

        demandeMereRepo.save(demande);
    }

    /**
     * Recherche par liste de statuts (pour les rôles backoffice globaux)
     */
    public Page<DemandeMere> searchDemandesParStatuts(
            String num, String demandeur, String type,
            List<Integer> statuts, String priorite,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size, String sort, String dir) {

        Pageable pageable = buildPageable(page, size, sort, dir);
        LocalDateTime from = buildFrom(dateFrom);
        LocalDateTime to   = buildTo(dateTo);

        List<Integer> st = (statuts == null || statuts.isEmpty()) ? null : statuts;

        return demandeMereRepo.searchMultiWithStatuts(
                clean(num), clean(demandeur), clean(type),
                st, from, to, pageable
        );
    }

    /**
     * Recherche par liste de statuts filtrée sur les demandeurIds visibles
     */
    public Page<DemandeMere> searchDemandesVisibleParStatuts(
            String num, String demandeur, String type,
            List<Integer> statuts, String priorite,
            LocalDate dateFrom, LocalDate dateTo,
            List<Integer> demandeurIds,
            int page, int size, String sort, String dir) {

        Pageable pageable = buildPageable(page, size, sort, dir);
        LocalDateTime from = buildFrom(dateFrom);
        LocalDateTime to   = buildTo(dateTo);

        List<Integer> st = (statuts == null || statuts.isEmpty()) ? null : statuts;

        return demandeMereRepo.searchMultiByDemandeurIdsAndStatuts(
                clean(num), clean(demandeur), clean(type),
                clean(priorite), st, from, to,
                demandeurIds.isEmpty() ? List.of(-1) : demandeurIds,
                pageable
        );
    }

// ── helpers privés ──────────────────────────────────────────────────────────

    private Pageable buildPageable(int page, int size, String sort, String dir) {
        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private LocalDateTime buildFrom(LocalDate d) {
        return d == null ? LocalDate.of(1900, 1, 1).atStartOfDay() : d.atStartOfDay();
    }

    private LocalDateTime buildTo(LocalDate d) {
        return d == null ? LocalDate.of(2999, 12, 31).atTime(23, 59, 59) : d.atTime(23, 59, 59);
    }

    private String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Pour les rôles backoffice (MG, Controleur, DFC, SG) :
     * - Les demandes dans les statutsAutorises (visibilité du rôle)
     * - OU les propres demandes de l'utilisateur (tous statuts)
     * Le tout en une seule requête SQL avec pagination native.
     */
    public Page<DemandeMere> searchDemandesBackoffice(
            String num, String demandeur, String type,
            List<Integer> statutsAutorises, Integer statutFilter,
            String priorite, String motif,LocalDate dateFrom, LocalDate dateTo,
            List<Integer> myVisibleIds,
            int page, int size, String sort, String dir) {

        Pageable pageable = buildPageable(page, size, sort, dir);
        LocalDateTime from = buildFrom(dateFrom);
        LocalDateTime to   = buildTo(dateTo);

        List<Integer> roleStatuts = (statutsAutorises == null || statutsAutorises.isEmpty())
                ? null : statutsAutorises;

        // Si un filtre statut est sélectionné, les demandes perso
        // doivent aussi respecter ce filtre
        Integer myStatutFilter = statutFilter;

        return demandeMereRepo.searchBackoffice(
                clean(num), clean(demandeur), clean(type),
                clean(priorite),
                clean(motif),
                roleStatuts,
                myStatutFilter,
                myVisibleIds.isEmpty() ? List.of(-1) : myVisibleIds,
                from, to, pageable
        );
    }

}
