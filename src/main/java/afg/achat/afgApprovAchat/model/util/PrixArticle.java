package afg.achat.afgApprovAchat.model.util;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "prix_article")
public class PrixArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prix")
    private int id;

    @ManyToOne
    @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    private Article article;

    @ManyToOne
    @JoinColumn(name = "id_bl_mere", referencedColumnName = "id_bl_mere")
    private BonLivraisonMere bonLivraisonMere;

    @Column(name = "prix_unitaire", nullable = false)
    private Double prixUnitaire;

    @Column(name = "date_prix", nullable = false)
    private LocalDate datePrix;
}
