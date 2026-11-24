package afg.achat.afgApprovAchat.model.demande;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Objet d'une demande Autre : une demande Autre porte une seule dépense,
 * sans article ni stock ni bon de commande ; elle est réglée en paiement direct.
 *
 * <p>Montant = quantité × montant unitaire (quantité 1 pour un sponsoring ou un remboursement,
 * nombre de repas pour une indemnité de repas…), dans la devise de la demande.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "demande_autre_detail")
public class DemandeAutreDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_demande_autre_detail")
    Integer id;

    @OneToOne
    @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere", nullable = false, unique = true)
    DemandeMere demandeMere;

    @ManyToOne
    @JoinColumn(name = "id_categorie_depense", referencedColumnName = "id_categorie_depense", nullable = false)
    CategorieDepense categorie;

    /** Personne ou organisme à payer. */
    @Column(name = "beneficiaire")
    String beneficiaire;

    @Column(name = "quantite")
    Double quantite;

    @Column(name = "montant_unitaire")
    Double montantUnitaire;

    @Column(name = "montant")
    Double montant;

    /** Fixe quantité et montant unitaire, et recalcule le montant. */
    public void definirMontant(double quantite, double montantUnitaire) {
        this.quantite = quantite;
        this.montantUnitaire = montantUnitaire;
        this.montant = quantite * montantUnitaire;
    }
}
