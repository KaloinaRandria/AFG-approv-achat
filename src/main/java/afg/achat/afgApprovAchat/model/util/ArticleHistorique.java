package afg.achat.afgApprovAchat.model.util;

import afg.achat.afgApprovAchat.model.Article;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "article_historique")
public class ArticleHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "id_article",referencedColumnName = "id_article",nullable = false)
    private Article article;

    @Column(name = "champ_modifie", nullable = false)
    private String champModifie;

    @Column(name = "ancienne_valeur")
    private String ancienneValeur;

    @Column(name = "nouvelle_valeur")
    private String nouvelleValeur;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @Column(name = "modifie_par")
    private String modifiePar; // Peut être l'email ou le nom d'utilisateur

    @Column(name = "code_article")
    private String codeArticle;

    public ArticleHistorique(Article article, String champModifie,
                             String ancienneValeur, String nouvelleValeur,
                             String modifiePar,String codeArticle) {
        this.article = article;
        this.champModifie = champModifie;
        this.ancienneValeur = ancienneValeur;
        this.nouvelleValeur = nouvelleValeur;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = modifiePar;
        this.codeArticle = codeArticle;
    }
}