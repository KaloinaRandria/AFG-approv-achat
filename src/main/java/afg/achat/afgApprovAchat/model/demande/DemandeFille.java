package afg.achat.afgApprovAchat.model.demande;

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
@Table(name = "demande_fille")
public class DemandeFille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_demande_fille")
    int id;
    @ManyToOne @JoinColumn(name = "id_demande_mere" , referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
    double quantite;

    public void setQuantite(String quantite) {
        this.quantite = Double.parseDouble(quantite);
    }

    public DemandeFille(DemandeMere demandeMere , Article article, String quantite) {
        this.setDemandeMere(demandeMere);
        this.setArticle(article);
        this.setQuantite(quantite);
    }
}
