package afg.achat.afgApprovAchat.model.bonSortie;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Entity
@Table(name = "bon_sortie_mere")
public class BonSortieMere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bon_sortie_mere")
    private int id;

    @Column(name = "numero_bs", unique = true, nullable = false)
    private String numeroBs; // ex: BS-DM2025001-1

    @ManyToOne
    @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere")
    private DemandeMere demandeMere;

    @ManyToOne
    @JoinColumn(name = "id_cree_par", referencedColumnName = "id_utilisateur")
    private Utilisateur creePar; // MG

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @Column(name = "date_confirmation")
    private LocalDateTime dateConfirmation;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    private StatutBonSortie statut = StatutBonSortie.BROUILLON;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    public enum StatutBonSortie {
        BROUILLON,   // En cours de saisie par le MG
        CONFIRME,    // Confirmé → stock impacté
        ANNULE       // Annulé avant confirmation
    }
}