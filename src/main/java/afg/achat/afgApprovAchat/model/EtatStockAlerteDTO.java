package afg.achat.afgApprovAchat.model;

import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.model.stock.StockAlerte;

public class EtatStockAlerteDTO {
    private String codeArticle;
    private String designation;
    private String seuilMin;
    private String totalEntree;
    private String totalSortie;
    private String stockDisponible;
    private StockAlerte alerte;

    // Constructeur à partir de VEtatStock
    public EtatStockAlerteDTO(VEtatStock etatStock) {
        this.codeArticle = etatStock.getCodeArticle();
        this.designation = etatStock.getDesignation();
        this.seuilMin = etatStock.getSeuilMin();
        this.totalEntree = etatStock.getTotalEntree();
        this.totalSortie = etatStock.getTotalSortie();
        this.stockDisponible = etatStock.getStockDisponible();
    }

    // Getters et Setters
    public String getCodeArticle() {
        return codeArticle;
    }

    public void setCodeArticle(String codeArticle) {
        this.codeArticle = codeArticle;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getSeuilMin() {
        return seuilMin;
    }

    public void setSeuilMin(String seuilMin) {
        this.seuilMin = seuilMin;
    }

    public String getTotalEntree() {
        return totalEntree;
    }

    public void setTotalEntree(String totalEntree) {
        this.totalEntree = totalEntree;
    }

    public String getTotalSortie() {
        return totalSortie;
    }

    public void setTotalSortie(String totalSortie) {
        this.totalSortie = totalSortie;
    }

    public String getStockDisponible() {
        return stockDisponible;
    }

    public void setStockDisponible(String stockDisponible) {
        this.stockDisponible = stockDisponible;
    }

    public StockAlerte getAlerte() {
        return alerte;
    }

    public void setAlerte(StockAlerte alerte) {
        this.alerte = alerte;
    }
}