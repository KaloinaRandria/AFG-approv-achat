package afg.achat.afgApprovAchat.service.paiement;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.paiement.PaiementDirect;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.paiement.PaiementDirectRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaiementDirectService {

    private final PaiementDirectRepo paiementDirectRepo;

    public Optional<PaiementDirect> getPaiementDirectParDemande(DemandeMere demande) {
        if (demande == null) return Optional.empty();
        return paiementDirectRepo.findByDemande(demande);
    }

    public Optional<PaiementDirect> getPaiementDirectParDemandeId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return paiementDirectRepo.findByDemandeId(id);
    }

    @Transactional
    public PaiementDirect creerPaiementDirectPourDemande(DemandeMere demande) {
        if (demande == null) return null;
        return paiementDirectRepo.findByDemande(demande)
                .orElseGet(() -> {
                    PaiementDirect pd = new PaiementDirect();
                    pd.setDemande(demande);
                    pd.setStatut(PaiementDirect.StatutPaiementDirect.A_TRANSMETTRE);
                    pd.setMontant(demande.getTotalEstime() != null ? demande.getTotalEstime() : demande.getTotalPrix());
                    return paiementDirectRepo.save(pd);
                });
    }

    @Transactional
    public boolean transmettreAFinance(DemandeMere demande, Utilisateur transmetteur, String commentaire) {
        if (demande == null || demande.getStatut() != StatutDemande.VALIDE) {
            return false;
        }

        PaiementDirect pd = creerPaiementDirectPourDemande(demande);
        if (pd == null || pd.getStatut() != PaiementDirect.StatutPaiementDirect.A_TRANSMETTRE) {
            return false;
        }

        pd.setStatut(PaiementDirect.StatutPaiementDirect.TRANSMISE_FINANCE);
        pd.setTransmisPar(transmetteur);
        pd.setDateTransmission(LocalDateTime.now());
        pd.setCommentaireTransmission(commentaire == null || commentaire.isBlank() ? null : commentaire.trim());
        paiementDirectRepo.save(pd);

        // Rétrocompatibilité sur l'entité DemandeMere
        demande.setStatutTransmissionFinance(DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE);
        demande.setTransmisFinancePar(transmetteur);
        demande.setDateTransmissionFinance(pd.getDateTransmission());
        demande.setCommentaireTransmissionFinance(pd.getCommentaireTransmission());

        return true;
    }

    @Transactional
    public boolean marquerCommePayee(DemandeMere demande, Utilisateur utilisateur, String commentaire) {
        if (demande == null || demande.getStatut() != StatutDemande.VALIDE) {
            return false;
        }

        PaiementDirect pd = creerPaiementDirectPourDemande(demande);
        if (pd == null || pd.getStatut() != PaiementDirect.StatutPaiementDirect.TRANSMISE_FINANCE) {
            return false;
        }

        pd.setStatut(PaiementDirect.StatutPaiementDirect.PAYEE);
        pd.setPayePar(utilisateur);
        pd.setDatePaiement(LocalDateTime.now());
        pd.setCommentairePaiement(commentaire == null || commentaire.isBlank() ? null : commentaire.trim());
        paiementDirectRepo.save(pd);

        // Rétrocompatibilité sur l'entité DemandeMere
        demande.setStatutTransmissionFinance(DemandeMere.StatutTransmissionFinance.PAYEE);

        return true;
    }

}
