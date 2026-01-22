package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandeFilleRepo extends JpaRepository<DemandeFille,Integer> {
    @Query("select dmf from DemandeFille dmf where dmf.demandeMere = :demandeMere")
    List<DemandeFille> findDemandeFilleByDemandeMere(DemandeMere demandeMere);
}
