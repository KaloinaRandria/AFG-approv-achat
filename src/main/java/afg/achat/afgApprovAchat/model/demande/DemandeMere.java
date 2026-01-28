package afg.achat.afgApprovAchat.model.demande;

import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.util.Adresse;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "demande_mere")
@SequenceGenerator(
        name = "demande_mere_id_demande_mere_seq",
        sequenceName = "demande_mere_id_demande_mere_seq",
        allocationSize = 1
)
public class DemandeMere {
    @Id
    @Column(name = "id_demande_mere")
    String id;
    @ManyToOne @JoinColumn(name = "id_demandeur", referencedColumnName = "id_utilisateur")
    Utilisateur demandeur;
    @ManyToOne @JoinColumn(name = "id_adresse", referencedColumnName = "id_adresse")
    Adresse adresse;
    @Column(name = "date_demande")
    LocalDateTime dateDemande;
    @Column(name = "date_sortie")
    LocalDateTime dateSortie;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_demande", nullable = false)
    StatutDemande statutDemande = StatutDemande.CREE;


    @Enumerated(EnumType.STRING)
    @Column(name = "nature_demande")
    NatureDemande natureDemande;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "motif_evoque", columnDefinition = "TEXT")
    String motifEvoque;

    @Enumerated(EnumType.STRING)
    PrioriteDemande priorite;

    @ManyToOne
    @JoinColumn(name = "id_centre_budgetaire", referencedColumnName = "id_centre_budgetaire")
    CentreBudgetaire centreBudgetaire;

    @Column(name = "total_prix")
    double totalPrix;

    public enum PrioriteDemande {
        BASSE,
        NORMALE,
        ELEVEE
    }
    public enum NatureDemande {
        OPEX,
        CAPEX
    }

    public enum StatutDemande {
        CREE,        // créée mais non soumise
        SOUMISE,          // envoyée pour validation
        EN_VALIDATION,    // en cours (optionnel)
        VALIDEE,          // approuvée
        REJETEE,          // refusée
        ANNULEE           // annulée par le demandeur
    }

    public void setId(IdGenerator idGenerator) {
        this.id = idGenerator.generateId("DM","demande_mere_id_demande_mere_seq");
    }

    public void setDateDemande(String dateDemande) {
        this.dateDemande = LocalDateTime.parse(dateDemande);
    }
    public void setDateSortie(String dateSortie) {
        this.dateSortie = LocalDateTime.parse(dateSortie);
    }
    public DemandeMere(Adresse adresse, String dateDemande, String dateSortie) {
        this.setAdresse(adresse);
        this.setDateDemande(dateDemande);
        this.setDateSortie(dateSortie);
    }
}
