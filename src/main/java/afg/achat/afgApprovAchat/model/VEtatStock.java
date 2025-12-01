package afg.achat.afgApprovAchat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "v_etat_stock")
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
}
