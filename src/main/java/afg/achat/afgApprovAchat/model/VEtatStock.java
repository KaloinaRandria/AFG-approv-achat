package afg.achat.afgApprovAchat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.beans.ConstructorProperties;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Immutable
// Vue en lecture seule : lue via @Subselect, que Hibernate ne compare pas au schéma
// (ddl-auto=validate). Les quantités numériques de la vue sont lues en texte.
@Subselect("SELECT * FROM v_etat_stock")
@Synchronize({"article", "stock_article", "udm"})
public class VEtatStock {
    @Id @Column(name = "id_article")
    int idArticle;
    @Column(name = "code_article")
    String codeArticle;
    @Column(name = "seuil_min")
    String seuilMin;
    String designation;
    @Column(name = "total_entree")
    String totalEntree;
    @Column(name = "total_sortie")
    String totalSortie;
    @Column(name = "stock_disponible")
    String stockDisponible;
    @Column(name = "unite_de_mesure")
    String udm;
    @Column(name = "desc_udm")
    String descUdm;
}
