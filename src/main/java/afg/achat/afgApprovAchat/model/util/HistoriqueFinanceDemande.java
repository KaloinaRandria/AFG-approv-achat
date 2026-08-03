package afg.achat.afgApprovAchat.model.util;

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
@Table(name = "historique_finance_demande")
public class HistoriqueFinanceDemande {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historique_finance_demande")
    int id;

    @ManyToOne
    @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;

    @ManyToOne
    @JoinColumn(name = "id_utilisateur", referencedColumnName = "id_utilisateur")
    Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_finance")
    ActionFinance actionFinance;

    @Column(name = "date_action")
    LocalDateTime dateAction;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    String commentaire;

    public enum ActionFinance {
        TRANSMISSION_FINANCE,
        PAIEMENT_EFFECTUE
    }
}
