package afg.achat.afgApprovAchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Immutable
// Vue en lecture seule : lue via @Subselect, que Hibernate ne compare pas au schéma
// (ddl-auto=validate). L'identifiant numérique de la vue est lu en texte.
@Subselect("SELECT * FROM v_historique_mouvement_stock")
public class HistoriqueMouvementStockView {

    @Id
    @Column(name = "id_stock_fille")
    private String idStockFille;

    @Column(name = "id_article")
    private String idArticle;

    @Column(name = "code_article")
    private String codeArticle;

    private String designation;

    @Column(name = "type_mouvement")
    private String typeMouvement;

    private Double quantite;

    @Column(name = "date_mouvement")
    private LocalDateTime dateMouvement;

    @Column(name = "ref_bl_mere")
    private String refBlMere;

    @Column(name = "ref_demande_mere")
    private String refDemandeMere;

    private String auteur;
    private String reference;

    private String udm;

    @Column(name = "desc_udm")
    private String descUdm;
}
