package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import afg.achat.afgApprovAchat.repository.demande.ValidationDemandeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValidationDemandeService {

    @Autowired
    private ValidationDemandeRepo validationDemandeRepo;

    public void logAction(ValidationDemande v) {
        validationDemandeRepo.save(v);
    }

    public List<ValidationDemande> getHistorique(DemandeMere demande) {
        return validationDemandeRepo
                .findByDemandeMereOrderByDateActionDesc(demande);
    }
}
