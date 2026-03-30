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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_bon_sortie_fille")
    int id;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
    @Column(name = "quantite_demandee")
    double quantiteDemandee;
    @Column(name = "quantite_sortie")
    double quantiteSortie;
    @ManyToOne @JoinColumn(name = "id_bon_sortie_mere", referencedColumnName = "id_bon_sortie_mere")
    BonSortieMere bonSortieMere;
    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    Statut statut = Statut.EN_ATTENTE;
    @Transient
    Double maxSortie;

    public enum Statut {
        EN_ATTENTE, // pas de sortie (stock insuffisant)
        SORTIE      // sortie effectuée
    }

}
