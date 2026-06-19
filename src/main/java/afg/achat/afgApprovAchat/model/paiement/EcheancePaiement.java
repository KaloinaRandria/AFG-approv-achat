package afg.achat.afgApprovAchat.model.paiement;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "echeance_paiement")
public class EcheancePaiement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name = "id_echeance_paiement")
    int id;
    @ManyToOne @JoinColumn(name = "id_bc_mere" , referencedColumnName = "id_bc_mere")
    BonCommandeMere bonCommandeMere;
    @Column(name = "numero_tranche")
    int numeroTranche;
    double montant;
    int pourcentage;
    @Column(name = "date_echeance")
    LocalDate dateEcheance;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    StatutEcheance statut;
    @Column(name = "date_paiement_reel")
    LocalDate datePaiementReel;

    public enum StatutEcheance {
        EN_ATTENTE,
        PAYEE,
        EN_RETARD
    }
}
