package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
