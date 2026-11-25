package afg.achat.afgApprovAchat.model.stock.inventaire;

import afg.achat.afgApprovAchat.model.Article;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un article à compter dans un inventaire.
 *
 * <p>{@link #stockTheorique} est le stock FIFO au moment du comptage (à l'ouverture tant que
 * l'article n'est pas compté) : l'écart {@code quantiteComptee - stockTheorique} est appliqué au
 * stock du jour de la validation, ce qui conserve les mouvements passés depuis le comptage.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventaire_ligne",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventaire_ligne_article",
                columnNames = {"id_inventaire", "id_article"}))
public class InventaireLigne {

    /** Écart toléré sur les quantités décimales. */
    public static final double TOLERANCE = 0.001;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventaire_ligne")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_inventaire", referencedColumnName = "id_inventaire")
    private Inventaire inventaire;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    private Article article;

    @Column(name = "stock_theorique", nullable = false)
    private double stockTheorique;

    /** {@code null} : pas encore compté. */
    @Column(name = "quantite_comptee")
    private Double quantiteComptee;

    @Column(name = "date_comptage")
    private LocalDateTime dateComptage;

    /** Prix unitaire de valorisation de l'écart, fixé à la validation. */
    @Column(name = "prix_unitaire")
    private Double prixUnitaire;

    /** Justification de l'écart (casse, erreur de sortie, réception non saisie…). */
    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    public boolean estCompte() {
        return quantiteComptee != null;
    }

    /** Compté − théorique ; 0 tant que l'article n'est pas compté. */
    public double getEcart() {
        return quantiteComptee == null ? 0 : quantiteComptee - stockTheorique;
    }

    public boolean aEcart() {
        return Math.abs(getEcart()) > TOLERANCE;
    }

    /** Valeur de l'écart (à la validation), sinon {@code null}. */
    public Double getValeurEcart() {
        return prixUnitaire == null ? null : getEcart() * prixUnitaire;
    }
}
