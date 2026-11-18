package afg.achat.afgApprovAchat.model.bonCommande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
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
@Table(name = "bon_commande_fille")
public class BonCommandeFille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_bc_fille")
    int id;
    @ManyToOne @JoinColumn(name = "id_bc_mere" , referencedColumnName = "id_bc_mere")
    BonCommandeMere bonCommandeMere;
    @ManyToOne @JoinColumn(name = "id_demande_fille" , referencedColumnName = "id_demande_fille")
    DemandeFille demandeFille;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
    @Column(name = "designation_fournisseur")
    String designationFournisseur;

    /** Désignation libre d'une ligne saisie à la main ; c'est elle qui est imprimée. */
    @Column(name = "designation", columnDefinition = "TEXT")
    String designation;
    /** Unité saisie à la main (ex. « Unité », « Lot ») quand la ligne n'a pas d'article. */
    @Column(name = "unite")
    String unite;

    /** Désignation imprimée : la désignation libre, sinon celle du fournisseur, sinon celle de l'article. */
    public String getDesignationAffichee() {
        if (designation != null && !designation.isBlank()) return designation;
        if (designationFournisseur != null && !designationFournisseur.isBlank()) return designationFournisseur;
        return article != null ? article.getDesignation() : "";
    }

    /** Unité imprimée : l'unité saisie, sinon celle de l'article. */
    public String getUniteAffichee() {
        if (unite != null && !unite.isBlank()) return unite;
        return article != null && article.getUdm() != null ? article.getUdm().getDescription() : "";
    }

    @Column(name = "quantite_commandee")
    double quantiteCommandee;
    @Column(name = "quantite_recue")
    double quantiteRecue;
    @Column(name = "quantite_restante")
    double quantiteRestante;

    @Column(name = "prix_unitaire_ht")
    double prixUnitaireHT;
    @Column(name = "montant_ht")
    double montantHT;
    @Column(name = "prix_unitaire_ttc")
    double prixUnitaireTTC;
    @Column(name = "montant_ttc")
    double montantTTC;

}
