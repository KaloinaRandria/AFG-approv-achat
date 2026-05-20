package afg.achat.afgApprovAchat.model.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lot_stock")
public class LotStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    private Article article;

    @ManyToOne
    @JoinColumn(name = "id_bon_livraison")
    private BonLivraisonMere bonLivraison;

    @Column(name = "quantite_initiale", nullable = false)
    private double quantiteInitiale;

    @Column(name = "quantite_restante", nullable = false)
    private double quantiteRestante;

    @Column(name = "prix_unitaire", nullable = false)
    private double prixUnitaire;

    @Column(name = "date_entree", nullable = false)
    private LocalDateTime dateEntree;

    public static LotStock creer(Article article, BonLivraisonMere bl,
                                 double quantite, double prix) {
        LotStock lot = new LotStock();
        lot.setArticle(article);
        lot.setBonLivraison(bl);
        lot.setQuantiteInitiale(quantite);
        lot.setQuantiteRestante(quantite);
        lot.setPrixUnitaire(prix);
        lot.setDateEntree(LocalDateTime.now());
        return lot;
    }
}