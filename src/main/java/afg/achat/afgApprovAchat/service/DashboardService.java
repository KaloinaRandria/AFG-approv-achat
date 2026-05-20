package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.DTO.DashboardStatsDTO;
import afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DemandeMereRepo demandeMereRepository;

    public DashboardStatsDTO computeStats(
            List<Integer> demandeurIds,
            boolean isAdminOrSpecial,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        int anneeActuelle = LocalDate.now().getYear();

        // Garde-fou : si null arrive quand même, on applique les bornes par défaut
        LocalDateTime from = (dateFrom != null ? dateFrom : LocalDate.of(anneeActuelle, 1,  1))
                .atStartOfDay();
        LocalDateTime to   = (dateTo   != null ? dateTo   : LocalDate.of(anneeActuelle, 12, 31))
                .atTime(23, 59, 59);

        // ── 1. Une seule requête pour charger le périmètre ────────────────
        List<DemandeMere> toutes = isAdminOrSpecial
                ? demandeMereRepository.findAllWithFilters(from, to)
                : demandeMereRepository.findByDemandeurIdsWithFilters(demandeurIds, from, to);

        List<String> ids = toutes.stream().map(DemandeMere::getId).toList();

        if (ids.isEmpty()) {
            return emptyStats();
        }

        // ── 2. Compteurs statut ───────────────────────────────────────────
        Map<Integer, Long> parStatut = new HashMap<>();
        demandeMereRepository.countByStatut(ids)
                .forEach(row -> parStatut.put((Integer) row[0], (Long) row[1]));

        long enCours   = toutes.stream()
                .filter(d -> d.getStatut() != StatutDemande.VALIDE
                        && d.getStatut() != StatutDemande.REFUSE)
                .count();
        long refusees  = parStatut.getOrDefault(StatutDemande.REFUSE,       0L);
        long terminees = parStatut.getOrDefault(StatutDemande.VALIDE,        0L);
        long attenteN1 = parStatut.getOrDefault(StatutDemande.CREE,          0L);
        long attenteN2 = parStatut.getOrDefault(StatutDemande.VALIDATION_N1, 0L);
        long attenteN3 = parStatut.getOrDefault(StatutDemande.VALIDATION_N2, 0L);
        long attenteN4 = parStatut.getOrDefault(StatutDemande.VALIDATION_N3, 0L);
        long attenteSG = parStatut.getOrDefault(StatutDemande.VALIDATION_N4, 0L);
        long attenteCodep = parStatut.getOrDefault(StatutDemande.DECISION_CODEP, 0L);

        // ── 3. Nature ─────────────────────────────────────────────────────
        Map<String, Long> parNature = new HashMap<>();
        demandeMereRepository.countByNature(ids)
                .forEach(row -> parNature.put(String.valueOf(row[0]), (Long) row[1]));

        long opex  = parNature.getOrDefault("OPEX",  0L);
        long capex = parNature.getOrDefault("CAPEX", 0L);

        // ── 4. Priorité ───────────────────────────────────────────────────
        Map<String, Long> parPriorite = new HashMap<>();
        demandeMereRepository.countByPriorite(ids)
                .forEach(row -> parPriorite.put(String.valueOf(row[0]), (Long) row[1]));

        long p0 = parPriorite.getOrDefault("P0", 0L);
        long p1 = parPriorite.getOrDefault("P1", 0L);
        long p2 = parPriorite.getOrDefault("P2", 0L);

        // ── 5. Évolution mensuelle ────────────────────────────────────────
        Map<String, long[]> parMois = new LinkedHashMap<>();
        demandeMereRepository.countByMoisAndStatut(ids).forEach(row -> {
            int annee  = ((Number) row[0]).intValue();
            int mois   = ((Number) row[1]).intValue();
            int statut = ((Number) row[2]).intValue();
            long count = ((Number) row[3]).longValue();
            String key = annee + "-" + String.format("%02d", mois);
            parMois.computeIfAbsent(key, k -> new long[3]);
            if      (statut == StatutDemande.REFUSE) parMois.get(key)[1] += count;
            else if (statut == StatutDemande.VALIDE) parMois.get(key)[2] += count;
            else                                     parMois.get(key)[0] += count;
        });

        List<Map<String, Object>> moisData = parMois.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mois",      e.getKey());
                    m.put("enCours",   e.getValue()[0]);
                    m.put("refusees",  e.getValue()[1]);
                    m.put("terminees", e.getValue()[2]);
                    return m;
                }).toList();

        // ── 6. Demandes par service ───────────────────────────────────────
        List<ServiceDemandeDTO> demandesParService =
                demandeMereRepository.countDemandesByService();

        return new DashboardStatsDTO(
                enCours, refusees, terminees,
                opex, capex,
                p0, p1, p2,
                attenteN1, attenteN2, attenteN3, attenteN4, attenteSG, attenteCodep,
                moisData, demandesParService
        );
    }

    private DashboardStatsDTO emptyStats() {
        return new DashboardStatsDTO(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of()
        );
    }
}