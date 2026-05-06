package afg.achat.afgApprovAchat.model;

import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "article")
@Indexed
public class Article {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_article")
    int id;
    @Column(name = "code_article", unique = true, nullable = false)
    @KeywordField(name = "codeArticle_kw", normalizer = "lowercase", sortable = Sortable.YES)  // ← recherche exacte/préfixe
    @FullTextField(name = "codeArticle_ft", analyzer = "french") // ← recherche textuelle
    String codeArticle;

    @FullTextField(analyzer = "french")  // ← recherche full-text avec accents
    String designation;

    @Column(name = "seuil_min")
    int seuilMin;

    @ManyToOne @JoinColumn(name = "id_udm" , referencedColumnName = "id_udm")
    @IndexedEmbedded(includePaths = {"description", "acronyme"})
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    Udm udm;

    @ManyToOne @JoinColumn(name = "id_famille" , referencedColumnName = "id_famille")
    @IndexedEmbedded(includePaths = {"description", "description_kw"})
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    Famille famille;
    @Column(name = "prix_unitaire")
    Double prixUnitaire;
    @Column(name = "code_provisoire")
    String codeProvisoire;

    public Article(String designation, int seuilMin, Udm udm, Famille famille) {
        this.setDesignation(designation);
        this.setSeuilMin(seuilMin);
        this.setUdm(udm);
        this.setFamille(famille);
    }

    public void setCodeArticle(IdGenerator idGenerator) {
        this.codeArticle = idGenerator.generateId("ART", "s_code_article");
    }

    public void setCodeArticle(String codeArticle) {
        this.codeArticle = codeArticle;
    }

    public void setPrixUnitaire(String prixUnitaire) {
        this.prixUnitaire = Double.parseDouble(prixUnitaire);
    }
}
