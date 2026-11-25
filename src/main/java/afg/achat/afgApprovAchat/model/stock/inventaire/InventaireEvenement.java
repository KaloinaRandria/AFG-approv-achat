package afg.achat.afgApprovAchat.model.stock.inventaire;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Journal d'un inventaire : ouverture, soumission, rejet (avec motif), validation, annulation. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventaire_evenement")
public class InventaireEvenement {

    public enum Action {
        OUVERTURE("Ouverture"),
        SOUMISSION("Soumission"),
        REJET("Rejet"),
        VALIDATION("Validation"),
        ANNULATION("Annulation");

        private final String libelle;
        Action(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventaire_evenement")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_inventaire", referencedColumnName = "id_inventaire")
    private Inventaire inventaire;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private Action action;

    @ManyToOne
    @JoinColumn(name = "id_utilisateur", referencedColumnName = "id_utilisateur")
    private Utilisateur auteur;

    @Column(name = "date_evenement", nullable = false)
    private LocalDateTime date;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    public InventaireEvenement(Inventaire inventaire, Action action, Utilisateur auteur, String commentaire) {
        this.inventaire = inventaire;
        this.action = action;
        this.auteur = auteur;
        this.commentaire = commentaire;
        this.date = LocalDateTime.now();
    }
}
