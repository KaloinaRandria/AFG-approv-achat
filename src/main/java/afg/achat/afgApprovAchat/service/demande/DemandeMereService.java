// DemandeMereService.java
package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.ModeTraitement;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereSpec;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereSpec.SearchCriteria;
import afg.achat.afgApprovAchat.repository.util.ModeTraitementRepo;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DemandeMereService {

    private final DemandeMereRepo   demandeMereRepo;
    private final DemandeFilleRepo  demandeFilleRepo;
    private final IdGenerator       idGenerator;
    private final ModeTraitementRepo modeTraitementRepo;

    // ── Lecture ──────────────────────────────────────────────────────────────

    public Optional<DemandeMere> getDemandeMereById(String id) {
        return demandeMereRepo.findById(id);
    }

    public DemandeMere getDemandeById(String id) {
        return demandeMereRepo.findById(id).orElse(null);
    }

    public DemandeMere[] getAllDemandesMeres() {
        return demandeMereRepo.findAll().toArray(new DemandeMere[0]);
    }

    /**
     * Point d'entrée unique pour toutes les recherches paginées.
     * Le controller construit le SearchCriteria adapté au rôle,
     * le service se contente d'exécuter.
     */
    public Page<DemandeMere> search(SearchCriteria criteria,
                                    int page, int size,
                                    String sort, String dir) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        return demandeMereRepo.findAll(DemandeMereSpec.build(criteria), pageable);
    }

    public List<ServiceDemandeDTO> getDemandesParService() {
        return demandeMereRepo.countDemandesByService();
    }

    // ── Écriture ─────────────────────────────────────────────────────────────

    public void saveDemandeMere(DemandeMere dm) {
        demandeMereRepo.save(dm);
    }

    @Transactional
    public void appliquerDecisionGlobale(DemandeMere demande, int nouveauStatut) {
        demande.setStatut(nouveauStatut);
        demandeMereRepo.save(demande);

        List<DemandeFille> filles = demandeFilleRepo.findDemandeFilleByDemandeMere(demande);
        filles.stream()
                .filter(f -> f.getStatut() != StatutDemande.REFUSE)
                .forEach(f -> f.setStatut(nouveauStatut));
        demandeFilleRepo.saveAll(filles);
    }

    @Transactional
    public DemandeMere getOrCreateByCodeProvisoire(String ref, LocalDateTime dt) {
        return demandeMereRepo.findByCodeProvisoire(ref)
                .orElseGet(() -> {
                    DemandeMere dm = new DemandeMere();
                    dm.setId(idGenerator);
                    dm.setCodeProvisoire(ref);
                    dm.setDateDemande(String.valueOf(dt));
                    return demandeMereRepo.save(dm);
                });
    }

    @Transactional
    public void recalculerTotal(DemandeMere demande) {
        double total = demandeFilleRepo.calculerTotal(demande, StatutDemande.REFUSE);
        demande.setTotalEstime(total);
        demandeMereRepo.save(demande);
    }

    // ── Helpers privés ───────────────────────────────────────────────────────

    private Pageable buildPageable(int page, int size, String sort, String dir) {
        String sortBy = (sort == null || sort.isBlank()) ? "dateDemande" : sort;
        Sort.Direction direction = "desc".equalsIgnoreCase(dir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    /** Conversion LocalDate → LocalDateTime avec bornes par défaut */
    public static LocalDateTime toFrom(LocalDate d) {
        return d == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : d.atStartOfDay();
    }

    public static LocalDateTime toTo(LocalDate d) {
        return d == null ? LocalDateTime.of(2999, 12, 31, 23, 59, 59) : d.atTime(23, 59, 59);
    }

    public boolean peutCreerBonCommande(DemandeMere demandeMere, boolean isMG) {
        if (demandeMere.getStatut() != StatutDemande.VALIDE) {
            return false;
        }
        if(!isMG) {
            return false;
        }
        ModeTraitement mode = demandeMere.getModeTraitement();
        if(mode == null) {
            return false;
        }

        return mode.isNecessiteBonCommande();
    }
}