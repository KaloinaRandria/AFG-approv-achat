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
    int statut;


    //Prix au moment de la demande (issu du dernier bon de livraison)
    @Column(name = "prix_unitaire")
    Double prixUnitaire;

    //Calculé = quantite * prixUnitaire
    @Column(name = "montant_estime")
    Double montantEstime;


    //Auto-calcul du montant estimé
    public void setPrixUnitaire(Double prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
        if (prixUnitaire != null) {
            this.montantEstime = this.quantite * prixUnitaire;
        }
    }

    public void setQuantite(String quantite) {
        this.quantite = Double.parseDouble(quantite);
    }

}
