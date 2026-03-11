package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.ValidationDemandeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ValidationDemandeService {

    @Autowired
    private ValidationDemandeRepo validationDemandeRepo;

    public void logAction(ValidationDemande v) {
        validationDemandeRepo.save(v);
    }

    public void logValidation(DemandeMere demande,
                               Utilisateur user,
                               String commentaire,
                               int etape) {

        ValidationDemande h = new ValidationDemande();
        h.setDemandeMere(demande);
        h.setValidateur(user);
        h.setEtape(etape);
        h.setDecision(ValidationDemande.DecisionValidation.APPROUVE);
        h.setDateAction(String.valueOf(LocalDateTime.now()));
        h.setCommentaire(commentaire);

        this.logAction(h);
    }

    public List<ValidationDemande> getHistorique(DemandeMere demande) {
        return validationDemandeRepo
                .findByDemandeMereOrderByDateActionAsc(demande);
    }
}
