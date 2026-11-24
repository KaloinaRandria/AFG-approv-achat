package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.HistoriqueFinanceDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.util.HistoriqueFinanceDemandeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HistoriqueFinanceDemandeService {
    @Autowired
    private HistoriqueFinanceDemandeRepo historiqueFinanceDemandeRepo;

    public void logTransmissionFinance(DemandeMere demande, Utilisateur utilisateur, String commentaire) {
        logAction(demande, utilisateur, HistoriqueFinanceDemande.ActionFinance.TRANSMISSION_FINANCE, commentaire);
    }

    public void logPaiementEffectue(DemandeMere demande, Utilisateur utilisateur, String commentaire) {
        logAction(demande, utilisateur, HistoriqueFinanceDemande.ActionFinance.PAIEMENT_EFFECTUE, commentaire);
    }

    public List<HistoriqueFinanceDemande> getHistoriqueByDemande(DemandeMere demande) {
        return historiqueFinanceDemandeRepo.findByDemandeMereOrderByDateActionAsc(demande);
    }

    private void logAction(DemandeMere demande,
                           Utilisateur utilisateur,
                           HistoriqueFinanceDemande.ActionFinance action,
                           String commentaire) {
        HistoriqueFinanceDemande h = new HistoriqueFinanceDemande();
        h.setDemandeMere(demande);
        h.setUtilisateur(utilisateur);
        h.setActionFinance(action);
        h.setDateAction(LocalDateTime.now());
        h.setCommentaire(commentaire == null || commentaire.isBlank() ? null : commentaire.trim());
        historiqueFinanceDemandeRepo.save(h);
    }
}
