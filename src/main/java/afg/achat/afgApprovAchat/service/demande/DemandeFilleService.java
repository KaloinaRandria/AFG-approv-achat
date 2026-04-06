package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DemandeFilleService {
    @Autowired
    DemandeFilleRepo demandeFilleRepo;
    @Autowired
    DemandeMereRepo demandeMereRepo;
    @Autowired
    private DemandeMereService demandeMereService;
    @Autowired
    private ValidationDemandeService validationDemandeService;

    public DemandeFille[] getAllDemandesFilles() {
        return demandeFilleRepo.findAll().toArray(new DemandeFille[0]);
    }

    public void saveDemandeFille(DemandeFille demandeFille) {
        demandeFilleRepo.save(demandeFille);
    }
    public List<DemandeFille> getDemandeFilleByDemandeMere(DemandeMere demandeMere) {
        return this.demandeFilleRepo.findDemandeFilleByDemandeMere(demandeMere);
    }
    public DemandeFille getDemandeFilleById(int id) {
        return this.demandeFilleRepo.findById(id).orElse(null);
    }

    @Transactional
    public void refuserLigne(DemandeFille ligne,
                             Utilisateur validateur,
                             String commentaire) {

        ligne.setStatut(StatutDemande.REFUSE);
        demandeFilleRepo.save(ligne);

        // Recalcul total
        demandeMereService.recalculerTotal(ligne.getDemandeMere());

        DemandeMere demande = ligne.getDemandeMere();

        // étape réelle du workflow
        int etapeCourante = demande.getStatut();

        // Historique propre
        ValidationDemande historique = new ValidationDemande();
        historique.setDemandeMere(demande);
        historique.setValidateur(validateur);

        historique.setEtape(etapeCourante); // étape réelle
        historique.setDecision(ValidationDemande.DecisionValidation.APPROUVE); // décision

        String designation = ligne.getArticle() != null
                ? ligne.getArticle().getDesignation()
                : "Article inconnu";

        String codeArticle = ligne.getArticle() != null
                ? ligne.getArticle().getCodeArticle()
                : "N/A";

        historique.setCommentaire(
                "Refus de l'article : " + codeArticle + " - " + designation +
                        (commentaire != null && !commentaire.isBlank()
                                ? " | Motif : " + commentaire
                                : "")
        );

        historique.setDateAction(String.valueOf(LocalDateTime.now()));

        validationDemandeService.logAction(historique);
    }

    public Article getArticleByIdDemandeFille(int idDemandeFille) {
        return demandeFilleRepo.findArticleByIdDemandeFille(idDemandeFille);
    }

    public List<DemandeFille> getDemandeFilleValideeByDemandeMere(DemandeMere demandeMere) {
        return demandeFilleRepo.findByDemandeMereAndStatutValidee(demandeMere);
    }

    public List<DemandeFille> getDemandeFilleByArticleCodeAndPrixNull(String codeArticle) {
        return demandeFilleRepo.findByArticleCodeAndPrixNull(codeArticle);
    }

    public Optional<Double> getPrixByDemandeAndArticle(String demandeId, String codeArticle) {
        return demandeFilleRepo
                .findByDemandeMereIdAndArticleCodeArticle(demandeId, codeArticle)
                .map(DemandeFille::getPrixUnitaire);
    }

    /**
     * Récupère la quantité totale réservée en stock pour un article dans une demande
     * @param demandeId L'ID de la demande mère
     * @param codeArticle Le code article
     * @return La quantité réservée en stock
     */
    public double getQuantiteStockReserveePourDemande(String demandeId, String codeArticle) {
        Double quantite = demandeFilleRepo.getQuantiteStockReserveePourDemande(demandeId, codeArticle);
        return quantite != null ? quantite : 0.0;
    }

}
