package afg.achat.afgApprovAchat.model.bonLivraison;

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
@Table(name = "bon_livraison_fille")
public class BonLivraisonFille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_bl_fille")
    int id;
    @ManyToOne @JoinColumn(name = "id_bl_mere", referencedColumnName = "id_bl_mere")
    BonLivraisonMere bonLivraisonMere;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
    @Column(name = "prix_unitaire")
    double prixUnitaire;
    @Column(name = "quantite_recu")
    double quantiteRecu;
    @Column(name = "quantite_demande")
    double quantiteDemande;

    public void setPrixUnitaire(String prixUnitaire) {
        this.prixUnitaire = Double.parseDouble(prixUnitaire);
    }

    public void setQuantiteRecu(String quantiteRecu) {
        this.quantiteRecu = Double.parseDouble(quantiteRecu);
    }

    public void setQuantiteDemande(String quantiteDemande) {
        this.quantiteDemande = Double.parseDouble(quantiteDemande);
    }

    public BonLivraisonFille(BonLivraisonMere bonLivraisonMere, Article article, String prixUnitaire, String quantiteRecu, String quantiteDemande) {
        this.setBonLivraisonMere(bonLivraisonMere);
        this.setArticle(article);
        this.setPrixUnitaire(prixUnitaire);
        this.setQuantiteRecu(quantiteRecu);
        this.setQuantiteDemande(quantiteDemande);
    }

}
