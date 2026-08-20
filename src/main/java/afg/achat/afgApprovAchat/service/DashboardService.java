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
        return computeStats(demandeurIds, isAdminOrSpecial, dateFrom, dateTo, null);
    }

    public DashboardStatsDTO computeStats(
            List<Integer> demandeurIds,
            boolean isAdminOrSpecial,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer currentUserId
    ) {
        int anneeActuelle = LocalDate.now().getYear();

        LocalDateTime from = (dateFrom != null ? dateFrom : LocalDate.of(anneeActuelle, 1, 1))
                .atStartOfDay();
        LocalDateTime to   = (dateTo   != null ? dateTo   : LocalDate.of(anneeActuelle, 12, 31))
                .atTime(23, 59, 59);

        // ── 1. Période précédente pour calcul Mo/Mo ───────────────────────
        long daysDiff = java.time.Duration.between(from, to).toDays();
        if (daysDiff <= 0) daysDiff = 30;
        LocalDateTime prevFrom = from.minusDays(daysDiff);
        LocalDateTime prevTo   = from.minusNanos(1);

        // ── 2. Statuts Période Courante ───────────────────────────────────
        List<Object[]> rowsStatut = isAdminOrSpecial
                ? demandeMereRepository.countByStatutAll(from, to)
                : (demandeurIds == null || demandeurIds.isEmpty() ? List.of() : demandeMereRepository.countByStatutByDemandeurIds(demandeurIds, from, to));

        Map<Integer, Long> parStatut = new HashMap<>();
        long totalDemandes = 0;
        for (Object[] row : rowsStatut) {
            int st = ((Number) row[0]).intValue();
            long c = ((Number) row[1]).longValue();
            parStatut.put(st, c);
            totalDemandes += c;
        }

        long refusees  = parStatut.getOrDefault(StatutDemande.REFUSE,       0L);
        long terminees = parStatut.getOrDefault(StatutDemande.VALIDE,        0L);
        long enCours   = totalDemandes - refusees - terminees;
        long attenteN1 = parStatut.getOrDefault(StatutDemande.CREE,          0L);
        long attenteN2 = parStatut.getOrDefault(StatutDemande.VALIDATION_N1, 0L);
        long attenteN3 = parStatut.getOrDefault(StatutDemande.VALIDATION_N2, 0L);
        long attenteN4 = parStatut.getOrDefault(StatutDemande.VALIDATION_N3, 0L);
        long attenteSG = parStatut.getOrDefault(StatutDemande.VALIDATION_N4, 0L);
        long attenteCodep = parStatut.getOrDefault(StatutDemande.DECISION_CODEP, 0L);

        // ── 3. Statuts Période Précédente (Tendances Mo/Mo) ───────────────
        List<Object[]> prevRowsStatut = isAdminOrSpecial
                ? demandeMereRepository.countByStatutAll(prevFrom, prevTo)
                : (demandeurIds == null || demandeurIds.isEmpty() ? List.of() : demandeMereRepository.countByStatutByDemandeurIds(demandeurIds, prevFrom, prevTo));

        Map<Integer, Long> prevParStatut = new HashMap<>();
        long prevTotalDemandes = 0;
        for (Object[] row : prevRowsStatut) {
            int st = ((Number) row[0]).intValue();
            long c = ((Number) row[1]).longValue();
            prevParStatut.put(st, c);
            prevTotalDemandes += c;
        }

        long prevRefusees  = prevParStatut.getOrDefault(StatutDemande.REFUSE, 0L);
        long prevTerminees = prevParStatut.getOrDefault(StatutDemande.VALIDE, 0L);
        long prevEnCours   = prevTotalDemandes - prevRefusees - prevTerminees;

        Double trendEnCours   = computeTrend(enCours, prevEnCours);
        Double trendRefusees  = computeTrend(refusees, prevRefusees);
        Double trendTerminees = computeTrend(terminees, prevTerminees);

        // ── 4. Nature (OPEX / CAPEX) ──────────────────────────────────────
        List<Object[]> rowsNature = isAdminOrSpecial
                ? demandeMereRepository.countByNatureAll(from, to)
                : (demandeurIds == null || demandeurIds.isEmpty() ? List.of() : demandeMereRepository.countByNatureByDemandeurIds(demandeurIds, from, to));
        Map<String, Long> parNature = new HashMap<>();
        rowsNature.forEach(row -> parNature.put(String.valueOf(row[0]), ((Number) row[1]).longValue()));

        long opex  = parNature.getOrDefault("OPEX",  0L);
        long capex = parNature.getOrDefault("CAPEX", 0L);

        // ── 5. Priorité (P0 / P1 / P2) ────────────────────────────────────
        List<Object[]> rowsPriorite = isAdminOrSpecial
                ? demandeMereRepository.countByPrioriteAll(from, to)
                : (demandeurIds == null || demandeurIds.isEmpty() ? List.of() : demandeMereRepository.countByPrioriteByDemandeurIds(demandeurIds, from, to));
        Map<String, Long> parPriorite = new HashMap<>();
        rowsPriorite.forEach(row -> parPriorite.put(String.valueOf(row[0]), ((Number) row[1]).longValue()));

        long p0 = parPriorite.getOrDefault("P0", 0L);
        long p1 = parPriorite.getOrDefault("P1", 0L);
        long p2 = parPriorite.getOrDefault("P2", 0L);

        // ── 6. Délais Moyens d'Approbation SLA Global ─────────────────────
        List<Object[]> rowsSla = isAdminOrSpecial
                ? demandeMereRepository.findAverageApprovalDelayByPriorityAll(from, to)
                : (demandeurIds == null || demandeurIds.isEmpty() ? List.of() : demandeMereRepository.findAverageApprovalDelayByPriorityByDemandeurIds(demandeurIds, from, to));
        Double slaP0 = null, slaP1 = null, slaP2 = null;
        for (Object[] row : rowsSla) {
            if (row[0] != null && row[1] != null) {
                String prio = String.valueOf(row[0]).trim();
                double avgDays = Math.round(((Number) row[1]).doubleValue() * 10.0) / 10.0;
                if ("P0".equalsIgnoreCase(prio)) slaP0 = avgDays;
                else if ("P1".equalsIgnoreCase(prio)) slaP1 = avgDays;
                else if ("P2".equalsIgnoreCase(prio)) slaP2 = avgDays;
            }
        }

        // ── 6b. SLA Personnel du Valideur Connecté ───────────────────────
        Double monSlaP0 = null, monSlaP1 = null, monSlaP2 = null;
        if (currentUserId != null) {
            List<Object[]> rowsMonSla = demandeMereRepository
                    .findAverageApprovalDelayByPriorityAndValidateurId(currentUserId, from, to);
            for (Object[] row : rowsMonSla) {
                if (row[0] != null && row[1] != null) {
                    String prio = String.valueOf(row[0]).trim();
                    double avgDays = Math.round(((Number) row[1]).doubleValue() * 10.0) / 10.0;
                    if ("P0".equalsIgnoreCase(prio)) monSlaP0 = avgDays;
                    else if ("P1".equalsIgnoreCase(prio)) monSlaP1 = avgDays;
                    else if ("P2".equalsIgnoreCase(prio)) monSlaP2 = avgDays;
                }
            }
        }

        // ── 7. Évolution Mensuelle ────────────────────────────────────────
        List<Object[]> rowsMois = isAdminOrSpecial
                ? demandeMereRepository.countByMoisAndStatutAll(from, to)
                : (demandeurIds == null || demandeurIds.isEmpty() ? List.of() : demandeMereRepository.countByMoisAndStatutByDemandeurIds(demandeurIds, from, to));
        Map<String, long[]> parMois = new LinkedHashMap<>();
        rowsMois.forEach(row -> {
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

        // ── 8. Demandes par service ───────────────────────────────────────
        List<ServiceDemandeDTO> demandesParService = demandeMereRepository.countDemandesByService();

        return new DashboardStatsDTO(
                enCours, refusees, terminees,
                opex, capex,
                p0, p1, p2,
                attenteN1, attenteN2, attenteN3, attenteN4, attenteSG, attenteCodep,
                moisData, demandesParService,
                slaP0, slaP1, slaP2,
                monSlaP0, monSlaP1, monSlaP2,
                trendEnCours, trendRefusees, trendTerminees
        );
    }

    private Double computeTrend(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round(((double) (current - previous) / previous * 100.0) * 10.0) / 10.0;
    }

    private DashboardStatsDTO emptyStats() {
        return new DashboardStatsDTO(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(),
                null, null, null,
                null, null, null,
                0.0, 0.0, 0.0
        );
    }
}