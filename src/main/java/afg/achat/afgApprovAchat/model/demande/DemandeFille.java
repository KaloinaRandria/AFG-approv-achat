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
    // ── Scission stock / achat (remplie par MG) ──────────────────────────
    @Column(name = "quantite_stock")
    private Double quantiteStock;       // partie dispo en stock

    @Column(name = "quantite_achat")
    private Double quantiteAchat;       // partie à acheter (proforma)

    @Column(name = "prix_unitaire_stock")
    private Double prixUnitaireStock;   // dernier prix BL (auto)

    @Column(name = "prix_unitaire_achat")
    private Double prixUnitaireAchat;   // proforma saisi par MG

    @Column(name = "montant_stock")
    private Double montantStock;        // quantiteStock × prixUnitaireStock

    @Column(name = "montant_achat")
    private Double montantAchat;        // quantiteAchat × prixUnitaireAchat

    @Enumerated(EnumType.STRING)
    @Column(name = "type_approvisionnement")
    private TypeApprovisionnement typeApprovisionnement = TypeApprovisionnement.ACHAT;

    public enum TypeApprovisionnement {
        STOCK,  // sortie depuis le stock existant
        ACHAT   // à commander (proforma)
    }

    // Auto-calcul montants
    public void setQuantiteStock(Double quantiteStock) {
        this.quantiteStock = quantiteStock;
        if (this.prixUnitaireStock != null)
            this.montantStock = quantiteStock * this.prixUnitaireStock;
    }

    public void setQuantiteAchat(Double quantiteAchat) {
        this.quantiteAchat = quantiteAchat;
        if (this.prixUnitaireAchat != null)
            this.montantAchat = quantiteAchat * this.prixUnitaireAchat;
    }

    public void setPrixUnitaireStock(Double prix) {
        this.prixUnitaireStock = prix;
        if (this.quantiteStock != null)
            this.montantStock = this.quantiteStock * prix;
    }

    public void setPrixUnitaireAchat(Double prix) {
        this.prixUnitaireAchat = prix;
        if (this.quantiteAchat != null)
            this.montantAchat = this.quantiteAchat * prix;
    }

    // Montant total de la ligne (stock + achat)
    public double getMontantTotal() {
        double ms = (montantStock != null) ? montantStock : 0.0;
        double ma = (montantAchat != null) ? montantAchat : 0.0;
        return ms + ma;
    }


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
