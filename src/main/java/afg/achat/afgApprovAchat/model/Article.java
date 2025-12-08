package afg.achat.afgApprovAchat.model;

import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
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
@Table(name = "article")
public class Article {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_article")
    int id;
    @Column(name = "code_article", unique = true, nullable = false)
    String codeArticle;
    String designation;
    @Column(name = "seuil_min")
    int seuilMin;
    @ManyToOne @JoinColumn(name = "id_udm" , referencedColumnName = "id_udm")
    Udm udm;
    @ManyToOne @JoinColumn(name = "id_famille" , referencedColumnName = "id_famille")
    Famille famille;
    @ManyToOne @JoinColumn(name = "id_centre_budgetaire" , referencedColumnName = "id_centre_budgetaire")
    CentreBudgetaire centreBudgetaire;

    public Article(String designation, int seuilMin, Udm udm, Famille famille, CentreBudgetaire centreBudgetaire) {
        this.setDesignation(designation);
        this.setSeuilMin(seuilMin);
        this.setUdm(udm);
        this.setFamille(famille);
        this.setCentreBudgetaire(centreBudgetaire);
    }

    public void setCodeArticle(IdGenerator idGenerator) {
        this.codeArticle = idGenerator.generateId("ART-", "s_code_article");
    }

    public void setCodeArticle(String codeArticle) {
        this.codeArticle = codeArticle;
    }
}
