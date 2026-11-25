package afg.achat.afgApprovAchat.model.utilisateur;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Demande de création, de modification ou de (dés)activation d'un compte utilisateur,
 * soumise par un créateur (IT + MAKER) et appliquée seulement après la validation
 * d'une autre personne (IT + CHECKER). Les données saisies sont conservées telles quelles
 * (JSON) jusqu'à la décision.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "demande_compte")
public class DemandeCompte {

    public enum TypeDemande {
        CREATION("Création"),
        MODIFICATION("Modification"),
        ACTIVATION("Réactivation"),
        DESACTIVATION("Désactivation");

        private final String libelle;
        TypeDemande(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    public enum Statut {
        EN_ATTENTE("En attente"),
        VALIDEE("Validée"),
        REJETEE("Rejetée"),
        ANNULEE("Annulée");

        private final String libelle;
        Statut(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_demande_compte")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_demande", nullable = false)
    private TypeDemande type;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.EN_ATTENTE;

    /** Compte concerné ; pour une création, renseigné une fois le compte créé. */
    @ManyToOne
    @JoinColumn(name = "id_utilisateur_cible", referencedColumnName = "id_utilisateur")
    private Utilisateur cible;

    /** « Prénom Nom (mail) » au moment de la demande (création : compte pas encore créé). */
    @Column(name = "libelle_cible")
    private String libelleCible;

    /** Saisie du formulaire (UtilisateurRequestDTO en JSON) ; vide pour une (dés)activation. */
    @Column(name = "donnees", columnDefinition = "TEXT")
    private String donnees;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_demandeur", referencedColumnName = "id_utilisateur")
    private Utilisateur demandeur;

    @Column(name = "date_demande", nullable = false)
    private LocalDateTime dateDemande;

    @ManyToOne
    @JoinColumn(name = "id_decideur", referencedColumnName = "id_utilisateur")
    private Utilisateur decideur;

    @Column(name = "date_decision")
    private LocalDateTime dateDecision;

    @Column(name = "motif_rejet", columnDefinition = "TEXT")
    private String motifRejet;

    public boolean estEnAttente() {
        return statut == Statut.EN_ATTENTE;
    }
}
