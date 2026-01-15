package afg.achat.afgApprovAchat.model.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "v_historique_mouvement_stock")
public class HistoriqueMouvementStockView {

    @Id
    @Column(name = "id_stock_fille")
    private Integer idStockFille;

    @Column(name = "id_article")
    private Integer idArticle;

    @Column(name = "code_article")
    private String codeArticle;

    private String designation;

    @Column(name = "type_mouvement")
    private String typeMouvement;

    private Double quantite;

    @Column(name = "date_mouvement")
    private LocalDateTime dateMouvement;

    private String auteur;

    private Integer reference;

    private String udm;

    @Column(name = "desc_udm")
    private String descUdm;
}