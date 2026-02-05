package afg.achat.afgApprovAchat.model.demande;

import afg.achat.afgApprovAchat.model.CentreBudgetaire;
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
    @Column(name = "date_demande")
    LocalDateTime dateDemande;
    @Column(name = "date_sortie") //date prevue livraison
    LocalDateTime dateSortie;


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

    int statut;

    public enum PrioriteDemande {
        P2,
        P1,
        P0
    }
    public enum NatureDemande {
        OPEX,
        CAPEX
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
    public DemandeMere(String dateDemande, String dateSortie) {
        this.setDateDemande(dateDemande);
        this.setDateSortie(dateSortie);
    }
}
