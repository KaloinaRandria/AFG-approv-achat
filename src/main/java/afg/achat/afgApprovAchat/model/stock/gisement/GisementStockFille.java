package afg.achat.afgApprovAchat.model.stock.gisement;

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
@Table(name = "gisement_stock_fille")
public class GisementStockFille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_gisement_stock_fille")
    int id;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
    @ManyToOne @JoinColumn(name = "id_gisement", referencedColumnName = "id_gisement")
    ExistantGisement existantGisement;
    @Column(name = "quantite_in")
    double quantiteIn;
    @Column(name = "quantite_out")
    double quantiteOut;
    @Column(name = "date_mouvement")
    LocalDateTime dateMouvement;
    @Column(name = "entree_hors_local")
    double entreeHorsLocal;
    @Column(name = "sortie_hors_local")
    double sortieHorsLocal;

    public void setQuantiteIn(String quantiteIn) {
        this.quantiteIn = Double.parseDouble(quantiteIn.replace(",", "."));
    }
    public void setQuantiteOut(String quantiteOut) {
        this.quantiteOut = Double.parseDouble(quantiteOut.replace(",", "."));
    }
    public void setEntreeHorsLocal(String entreeHorsLocal) {
        this.entreeHorsLocal = Double.parseDouble(entreeHorsLocal.replace(",", "."));
    }
    public void setSortieHorsLocal(String sortieHorsLocal) {
        this.sortieHorsLocal = Double.parseDouble(sortieHorsLocal.replace(",", "."));
    }
    public void setDateMouvement(String dateMouvement) {
        this.dateMouvement = LocalDateTime.parse(dateMouvement);
    }

    public GisementStockFille(Article article, ExistantGisement existantGisement, String quantiteIn, String quantiteOut, String dateMouvement, String entreeHorsLocal, String sortieHorsLocal) {
        this.setArticle(article);
        this.setExistantGisement(existantGisement);
        this.setQuantiteIn(quantiteIn);
        this.setQuantiteOut(quantiteOut);
        this.setDateMouvement(dateMouvement);
        this.setEntreeHorsLocal(entreeHorsLocal);
        this.setSortieHorsLocal(sortieHorsLocal);

    }
}
