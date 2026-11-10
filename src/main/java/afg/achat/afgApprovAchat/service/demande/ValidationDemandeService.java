package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.ValidationDemandeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    /** Trace une action (modification, ajout de PJ…) sans changement de statut. */
    public void journaliser(DemandeMere demande, Utilisateur auteur, int etape, String commentaire) {
        enregistrer(demande, auteur, etape, ValidationDemande.DecisionValidation.APPROUVE, commentaire);
    }

    public void logRefus(DemandeMere demande, Utilisateur auteur, String commentaire, int etape) {
        enregistrer(demande, auteur, etape, ValidationDemande.DecisionValidation.REFUSE, commentaire);
    }

    /** Une seule entrée d'historique pour un lot de pièces jointes ; rien si la liste est vide. */
    public void journaliserPiecesJointes(DemandeMere demande, Utilisateur auteur, int etape,
                                         List<String> noms, String qualificatif) {
        if (noms == null || noms.isEmpty()) return;
        String liste = noms.stream().map(n -> "• " + n).collect(Collectors.joining("\n"));
        String type = (qualificatif == null || qualificatif.isBlank()) ? "" : qualificatif + " ";
        journaliser(demande, auteur, etape,
                noms.size() + " pièce(s) jointe(s) " + type + "ajoutée(s) :\n" + liste);
    }

    private void enregistrer(DemandeMere demande, Utilisateur auteur, int etape,
                             ValidationDemande.DecisionValidation decision, String commentaire) {
        ValidationDemande h = new ValidationDemande();
        h.setDemandeMere(demande);
        h.setValidateur(auteur);
        h.setEtape(etape);
        h.setDecision(decision);
        h.setCommentaire(commentaire);
        h.setDateAction(String.valueOf(LocalDateTime.now()));
        logAction(h);
    }

    public List<ValidationDemande> getHistorique(DemandeMere demande) {
        return validationDemandeRepo
                .findByDemandeMereOrderByDateActionAsc(demande);
    }
}
