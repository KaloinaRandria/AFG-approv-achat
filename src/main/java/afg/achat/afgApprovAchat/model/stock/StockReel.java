package afg.achat.afgApprovAchat.model.stock;

import afg.achat.afgApprovAchat.model.Article;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_reel")
public class StockReel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_stock_reel")
    int id;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
    @Column(name = "date_inventaire")
    LocalDateTime dateInventaire;
    @Column(name = "stock_reel")
    double stockReel;

    public void setDateInventaire(String dateInventaire) {
        this.dateInventaire = LocalDateTime.parse(dateInventaire);
    }

    public void setStockReel(String stockReel) {
        this.stockReel = Double.parseDouble(stockReel);
    }

    public StockReel(Article article, String dateInventaire, String stockReel) {
        this.setArticle(article);
        this.setDateInventaire(dateInventaire);
        this.setStockReel(stockReel);
    }
}
