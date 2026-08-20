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
