package afg.achat.afgApprovAchat.model.perdiem;

import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un taux du barème des per diem : montant <b>par jour</b> de mission pour une catégorie de personnel
 * et une zone, valable à partir d'une date d'effet. Il couvre les repas et les déplacements locaux ;
 * l'hébergement est payé directement par la banque.
 *
 * <p>Circuit maker / checker : le RH MAKER propose ({@link Statut#PROPOSE}), le RH CHECKER (DRH)
 * valide ou rejette. Un taux validé n'est jamais modifié : un nouveau taux pour la même
 * catégorie et la même zone clôt le précédent ({@code dateFin} = date d'effet du nouveau).
 * Les missions copieront le montant au moment du calcul : changer le barème ne les modifie pas.</p>
 *
 * <p>Période de validité : {@code dateEffet ≤ date < dateFin} ({@code dateFin} exclue, {@code null} = en vigueur).</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bareme_per_diem")
public class BaremePerDiem {

    /** Les noms des constantes sont enregistrés en base : ne pas les renommer. */
    public enum Statut {
        PROPOSE("En attente de validation"),
        VALIDE("Validé"),
        REJETE("Rejeté"),
        ANNULE("Annulé");

        private final String libelle;

        Statut(String libelle) { this.libelle = libelle; }

        public String getLibelle() { return libelle; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bareme_per_diem")
    Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_categorie_personnel", referencedColumnName = "id_categorie_personnel", nullable = false)
    CategoriePersonnel categorie;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_zone_mission", referencedColumnName = "id_zone_mission", nullable = false)
    ZoneMission zone;

    /** Montant par jour de mission. */
    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    BigDecimal montant;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_devise", referencedColumnName = "id_devise", nullable = false)
    Devise devise;

    @Column(name = "date_effet", nullable = false)
    LocalDate dateEffet;

    /** Premier jour où le taux ne s'applique plus ; {@code null} tant qu'il est en vigueur. */
    @Column(name = "date_fin")
    LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    Statut statut;

    @ManyToOne
    @JoinColumn(name = "id_propose_par", referencedColumnName = "id_utilisateur")
    Utilisateur proposePar;

    @Column(name = "date_proposition")
    LocalDateTime dateProposition;

    /** Justification de la proposition (note de service, revalorisation…). */
    @Column(name = "motif", columnDefinition = "TEXT")
    String motif;

    /** Valideur, auteur du rejet ou de l'annulation. */
    @ManyToOne
    @JoinColumn(name = "id_traite_par", referencedColumnName = "id_utilisateur")
    Utilisateur traitePar;

    @Column(name = "date_traitement")
    LocalDateTime dateTraitement;

    @Column(name = "commentaire_traitement", columnDefinition = "TEXT")
    String commentaireTraitement;

    /** Le taux est validé et s'applique à cette date. */
    public boolean estApplicableLe(LocalDate date) {
        return statut == Statut.VALIDE && date != null && dateEffet != null
                && !date.isBefore(dateEffet)
                && (dateFin == null || date.isBefore(dateFin));
    }

    public boolean estEnAttente() {
        return statut == Statut.PROPOSE;
    }
}
