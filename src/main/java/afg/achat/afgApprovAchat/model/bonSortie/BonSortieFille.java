package afg.achat.afgApprovAchat.model.bonSortie;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Entity
@Table(name = "bon_sortie_fille")
public class BonSortieFille {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bon_sortie_fille")
    private int id;

    @ManyToOne
    @JoinColumn(name = "id_bon_sortie_mere", referencedColumnName = "id_bon_sortie_mere")
    private BonSortieMere bonSortieMere;

    @ManyToOne
    @JoinColumn(name = "id_demande_fille", referencedColumnName = "id_demande_fille")
    private DemandeFille demandeFille; // lien vers la ligne de demande originale

    @ManyToOne
    @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    private Article article;

    @Column(name = "quantite_sortie")
    private double quantiteSortie;

    @Column(name = "prix_unitaire")
    private double prixUnitaire; // snapshot du prix au moment de la sortie

    @Column(name = "montant_total")
    private double montantTotal; // quantiteSortie * prixUnitaire

    public void setQuantiteSortie(double quantiteSortie) {
        this.quantiteSortie = quantiteSortie;
        this.montantTotal   = quantiteSortie * this.prixUnitaire;
    }

    public void setPrixUnitaire(double prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
        this.montantTotal = this.quantiteSortie * prixUnitaire;
    }
}