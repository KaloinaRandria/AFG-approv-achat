package afg.achat.afgApprovAchat.DTO;

import java.util.List;
import java.util.Map;

public record DashboardStatsDTO(
        long enCours,
        long refusees,
        long terminees,
        long opex,
        long capex,
        long p0, long p1, long p2,
        long attenteN1, long attenteN2, long attenteN3,
        long attenteN4, long attenteSG, long attenteCodep,
        List<Map<String, Object>> moisData,
        List<ServiceDemandeDTO> demandesParService,
        Double slaP0,
        Double slaP1,
        Double slaP2,
        Double monSlaP0,
        Double monSlaP1,
        Double monSlaP2,
        Double trendEnCours,
        Double trendRefusees,
        Double trendTerminees
) {}