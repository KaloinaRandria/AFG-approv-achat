package afg.achat.afgApprovAchat.service.stock;

public interface HistoriqueMouvementStockProjection {
    Integer getId_stock_fille();
    Integer getId_article();
    String getCode_article();
    String getDesignation();

    String getType_mouvement();
    Double getQuantite();
    java.time.LocalDateTime getDate_mouvement();

    Integer getRef_bl_mere();
    Integer getRef_demande_mere();

    String getAuteur();
    Integer getReference();

    String getUdm();
    String getDesc_udm();
}

