package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.HistoriqueFinanceDemande;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriqueFinanceDemandeRepo extends JpaRepository<HistoriqueFinanceDemande, Integer> {
    List<HistoriqueFinanceDemande> findByDemandeMereOrderByDateActionAsc(DemandeMere demandeMere);
}
