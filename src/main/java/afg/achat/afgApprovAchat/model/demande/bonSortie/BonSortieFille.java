package afg.achat.afgApprovAchat.model.demande.bonSortie;

import afg.achat.afgApprovAchat.model.Article;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bon_sortie_fille")
public class BonSortieFille {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bon_sortie_fille")
    int id;

    @ManyToOne
    @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;

    @Column(name = "quantite_demandee")
    double quantiteDemandee;

    @Column(name = "quantite_sortie")
    double quantiteSortie;

    @Column(name = "prix_unitaire")
    Double prixUnitaire;

    @ManyToOne
    @JoinColumn(name = "id_bon_sortie_mere", referencedColumnName = "id_bon_sortie_mere")
    BonSortieMere bonSortieMere;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    Statut statut = Statut.EN_ATTENTE;

    Double maxSortie;

    // Prix total calculé (quantiteSortie * prixUnitaire)
    @Transient
    public double getTotalLigne() {
        if (prixUnitaire == null || quantiteSortie <= 0) return 0.0;
        return quantiteSortie * prixUnitaire;
    }

    public enum Statut {
        EN_ATTENTE,
        SORTIE
    }
}
