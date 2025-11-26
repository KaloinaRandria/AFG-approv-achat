package afg.achat.afgApprovAchat.model.stock;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
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
@Table(name = "stock_mere")
public class StockMere {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_stock_mere")
    int id;
    @ManyToOne @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;
    @ManyToOne @JoinColumn(name = "id_bl_mere", referencedColumnName = "id_bl_mere")
    BonLivraisonMere bonLivraisonMere;

    public StockMere(DemandeMere demandeMere, BonLivraisonMere bonLivraisonMere) {
        this.setDemandeMere(demandeMere);
        this.setBonLivraisonMere(bonLivraisonMere);
    }
}
