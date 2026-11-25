package afg.achat.afgApprovAchat.model.stock.inventaire;

import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Inventaire physique du stock : complet (tous les articles actifs) ou limité à une famille.
 *
 * <p>Circuit maker / checker des Moyens Généraux : l'inventaire est ouvert et compté
 * ({@link Statut#EN_COURS}), soumis ({@link Statut#SOUMIS}), puis validé par une autre personne
 * ({@link Statut#VALIDE}) : c'est seulement à la validation que les écarts sont passés en stock.
 * Un rejet le renvoie en comptage avec un motif.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventaire")
public class Inventaire {

    public enum Statut {
        EN_COURS("En cours de comptage"),
        SOUMIS("En attente de validation"),
        VALIDE("Validé"),
        ANNULE("Annulé");

        private final String libelle;
        Statut(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventaire")
    private Integer id;

    /** INV-AAAA-NNNN, attribuée à l'ouverture. */
    @Column(name = "reference", unique = true, length = 30)
    private String reference;

    /** Famille inventoriée ; {@code null} : inventaire complet. */
    @ManyToOne
    @JoinColumn(name = "id_famille", referencedColumnName = "id_famille")
    private Famille famille;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private Statut statut = Statut.EN_COURS;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_cree_par", referencedColumnName = "id_utilisateur")
    private Utilisateur creePar;

    @Column(name = "date_ouverture", nullable = false)
    private LocalDateTime dateOuverture;

    @ManyToOne
    @JoinColumn(name = "id_soumis_par", referencedColumnName = "id_utilisateur")
    private Utilisateur soumisPar;

    @Column(name = "date_soumission")
    private LocalDateTime dateSoumission;

    /** Valideur (validation) ou auteur de l'annulation. */
    @ManyToOne
    @JoinColumn(name = "id_valide_par", referencedColumnName = "id_utilisateur")
    private Utilisateur validePar;

    @Column(name = "date_cloture")
    private LocalDateTime dateCloture;

    /** Dernier motif de rejet (le détail est dans le journal). */
    @Column(name = "motif_rejet", columnDefinition = "TEXT")
    private String motifRejet;

    @OneToMany(mappedBy = "inventaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InventaireLigne> lignes = new ArrayList<>();

    @OneToMany(mappedBy = "inventaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("date ASC, id ASC")
    private List<InventaireEvenement> evenements = new ArrayList<>();

    public boolean estEnCours() { return statut == Statut.EN_COURS; }
    public boolean estSoumis()  { return statut == Statut.SOUMIS; }
    /** En cours ou soumis : le stock n'est pas encore ajusté. */
    public boolean estOuvert()  { return statut == Statut.EN_COURS || statut == Statut.SOUMIS; }

    public String getLibellePerimetre() {
        return famille == null ? "Inventaire complet" : famille.getDescription();
    }
}
