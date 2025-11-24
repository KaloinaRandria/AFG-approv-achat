package afg.achat.afgApprovAchat.model.demande.bordereau;

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
@Table(name = "bordereau_fille")
public class BordereauFille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_bordereau_fille")
    int id;
    @ManyToOne @JoinColumn(name = "id_bordereau_mere", referencedColumnName = "id_bordereau_mere")
    BordereauMere bordereauMere;
    @ManyToOne @JoinColumn(name = "id_demande_fille", referencedColumnName = "id_demande_fille")
    DemandeFille  demandeFille;
    @Column(name = "prix_unitaire")
    double prixUnitaire;
    @Column(name = "quantite_sortie")
    double quantiteSortie;

    public void setPrixUnitaire(String prixUnitaire) {
        this.prixUnitaire = Double.parseDouble(prixUnitaire);
    }

    public void setQuantiteSortie(String quantiteSortie) {
        this.quantiteSortie = Double.parseDouble(quantiteSortie);
    }

    public BordereauFille(BordereauMere bordereauMere, DemandeFille demandeFille, String prixUnitaire, String quantiteSortie) {
        this.setBordereauMere(bordereauMere);
        this.setDemandeFille(demandeFille);
        this.setPrixUnitaire(prixUnitaire);
        this.setQuantiteSortie(quantiteSortie);
    }
}
