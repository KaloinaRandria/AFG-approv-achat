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
@Table(name = "reappro_notification")
public class ReapproNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_reappro_notification")
    int id;
    Boolean lue;
    @Column(name = "quantite_recommandee")
    double quantiteRecommandee;
    @ManyToOne @JoinColumn(name = "id_article", referencedColumnName = "id_article")
    Article article;
    @Column(name = "date_creation")
    LocalDateTime dateCreation;

    public void setQuantiteRecommandee(String quantiteRecommandee) {
        this.quantiteRecommandee = Double.parseDouble(quantiteRecommandee);
    }

    public void setDateCreation(String dateCreation) {
        this.dateCreation = LocalDateTime.parse(dateCreation);
    }
}
