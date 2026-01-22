package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DemandeFilleService {
    @Autowired
    DemandeFilleRepo demandeFilleRepo;

    public DemandeFille[] getAllDemandesFilles() {
        return demandeFilleRepo.findAll().toArray(new DemandeFille[0]);
    }

    public void saveDemandeFille(DemandeFille demandeFille) {
        demandeFilleRepo.save(demandeFille);
    }
    public List<DemandeFille> getDemandeFilleByDemandeMere(DemandeMere demandeMere) {
        return this.demandeFilleRepo.findDemandeFilleByDemandeMere(demandeMere);
    }
}
