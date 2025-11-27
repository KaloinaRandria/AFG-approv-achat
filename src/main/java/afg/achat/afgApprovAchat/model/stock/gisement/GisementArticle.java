package afg.achat.afgApprovAchat.model.stock.gisement;

import afg.achat.afgApprovAchat.model.Article;
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
@Table(name = "gisement_article")
public class GisementArticle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_gisement_article")
    int id;
    double capaciteMax;
    @ManyToOne @JoinColumn(name = "id_gisement", referencedColumnName = "id_gisement")
    ExistantGisement existantGisement;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
}
