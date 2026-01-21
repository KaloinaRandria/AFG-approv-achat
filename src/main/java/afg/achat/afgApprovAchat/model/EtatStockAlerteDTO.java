package afg.achat.afgApprovAchat.model;

import afg.achat.afgApprovAchat.model.stock.StockAlerte;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EtatStockAlerteDTO {
    private String codeArticle;
    private String designation;
    private String seuilMin;
    private String totalEntree;
    private String totalSortie;
    private String stockDisponible;
    private StockAlerte alerte;
    private String udm;
    private String desc_udm;

    // Constructeur à partir de VEtatStock
    public EtatStockAlerteDTO(VEtatStock etatStock) {
        this.codeArticle = etatStock.getCodeArticle();
        this.designation = etatStock.getDesignation();
        this.seuilMin = etatStock.getSeuilMin();
        this.totalEntree = etatStock.getTotalEntree();
        this.totalSortie = etatStock.getTotalSortie();
        this.stockDisponible = etatStock.getStockDisponible();
        this.udm = etatStock.getUdm();
        this.desc_udm = etatStock.getDescUdm();
    }
}