package afg.achat.afgApprovAchat.model.paiement;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
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
@Table(name = "paiement_direct")
public class PaiementDirect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paiement_direct")
    private Long id;

    @OneToOne
    @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere", nullable = false, unique = true)
    private DemandeMere demande;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    private StatutPaiementDirect statut = StatutPaiementDirect.A_TRANSMETTRE;

    @Column(name = "montant")
    private Double montant;

    @ManyToOne
    @JoinColumn(name = "id_transmis_par", referencedColumnName = "id_utilisateur")
    private Utilisateur transmisPar;

    @Column(name = "date_transmission")
    private LocalDateTime dateTransmission;

    @ManyToOne
    @JoinColumn(name = "id_paye_par", referencedColumnName = "id_utilisateur")
    private Utilisateur payePar;

    @Column(name = "date_paiement")
    private LocalDateTime datePaiement;

    @Column(name = "commentaire_transmission", columnDefinition = "TEXT")
    private String commentaireTransmission;

    @Column(name = "commentaire_paiement", columnDefinition = "TEXT")
    private String commentairePaiement;

    public enum StatutPaiementDirect {
        A_TRANSMETTRE,
        TRANSMISE_FINANCE,
        PAYEE
    }
}
