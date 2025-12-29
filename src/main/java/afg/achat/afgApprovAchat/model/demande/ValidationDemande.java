package afg.achat.afgApprovAchat.model.demande;

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
@Table(name = "validation_demande")
public class ValidationDemande {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_validation_demande")
    int id;
    @ManyToOne @JoinColumn(name = "id_demande_mere" , referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;
    @ManyToOne @JoinColumn(name = "id_validateur" , referencedColumnName = "id_utilisateur")
    Utilisateur validateur;
    @Column(name = "statut_demande")
    @Enumerated(EnumType.STRING)
    DemandeMere.StatutDemande statut;
    @Column(name = "date_action")
    LocalDateTime dateAction;

    public void setDateAction(String dateAction) {
        this.dateAction = LocalDateTime.parse(dateAction);
    }
}
