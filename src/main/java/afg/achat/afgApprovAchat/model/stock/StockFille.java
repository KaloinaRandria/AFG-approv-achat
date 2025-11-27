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
        this.entree = Double.parseDouble(entree);
    }

    public void setSortie(String sortie) {
        this.sortie = Double.parseDouble(sortie);
    }

    public StockFille(StockMere stockMere, Article article, String entree, String sortie) {
        this.setStockMere(stockMere);
        this.setArticle(article);
        this.setEntree(entree);
        this.setSortie(sortie);
    }
}
