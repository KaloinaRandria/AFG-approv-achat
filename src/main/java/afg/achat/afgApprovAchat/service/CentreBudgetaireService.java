package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.repository.CentreBudgetaireRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CentreBudgetaireService {
    @Autowired
    CentreBudgetaireRepo centreBudgetaireRepo;

    public CentreBudgetaire[] getAllCentreBudgetaires() {
        return centreBudgetaireRepo.findAll().toArray(new CentreBudgetaire[0]);
    }

    public Optional<CentreBudgetaire> getCentreBudgetaireById(int id) {
        return centreBudgetaireRepo.findById(id);
    }

    public CentreBudgetaire getCentreBudgetaireByCodeCentre(String codeCentre) {
        return this.centreBudgetaireRepo.findCentreBudgetaireByCodeCentre(codeCentre);
    }

}
