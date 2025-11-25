package afg.achat.afgApprovAchat.model.demande;

import afg.achat.afgApprovAchat.model.util.Adresse;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
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
public class DemandeMere {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_demande_mere")
    int id;
    @ManyToOne @JoinColumn(name = "id_demandeur", referencedColumnName = "id_utilisateur")
    Utilisateur demandeur;
    @ManyToOne @JoinColumn(name = "id_adresse", referencedColumnName = "id_adresse")
    Adresse adresse;
    @Column(name = "date_demande")
    LocalDateTime dateDemande;
    @Column(name = "date_sortie")
    LocalDateTime dateSortie;
    @Column(name = "est_valider")
    Boolean estValider;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature_demande")
    NatureDemande natureDemande;


    public enum NatureDemande {
        OPEX,
        CAPEX
    }


    public void setDateDemande(String dateDemande) {
        this.dateDemande = LocalDateTime.parse(dateDemande);
    }
    public void setDateSortie(String dateSortie) {
        this.dateSortie = LocalDateTime.parse(dateSortie);
    }
    public DemandeMere(Adresse adresse, String dateDemande, String dateSortie, Boolean estValider) {
        this.setAdresse(adresse);
        this.setDateDemande(dateDemande);
        this.setDateSortie(dateSortie);
        this.setEstValider(estValider);
    }
}
