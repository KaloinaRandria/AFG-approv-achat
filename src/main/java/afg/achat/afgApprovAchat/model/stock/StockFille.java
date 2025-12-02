package afg.achat.afgApprovAchat.model.stock;

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
@Table(name = "stock_fille")
public class StockFille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_stock_fille")
    int id;
    @ManyToOne @JoinColumn(name = "id_stock_mere",referencedColumnName = "id_stock_mere")
    StockMere stockMere;
    @ManyToOne @JoinColumn(name = "id_article",referencedColumnName = "id_article")
    Article article;
    double entree;
    double sortie;

    public void setEntree(String entree) {
        double val = Double.parseDouble(entree);
        if (val < 0) {
            throw new IllegalArgumentException("La valeur d'entrée ne peut pas être négative");
        }
        this.entree = val;
    }

    public void setSortie(String sortie) {
        double val = Double.parseDouble(sortie);
        if (val < 0) {
            throw new IllegalArgumentException("La valeur de sortie ne peut pas être négative");
        }
        this.sortie = val;
    }

    public StockFille(StockMere stockMere, Article article, String entree, String sortie) {
        this.setStockMere(stockMere);
        this.setArticle(article);
        this.setEntree(entree);
        this.setSortie(sortie);
    }
}
