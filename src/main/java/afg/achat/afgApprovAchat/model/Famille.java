package afg.achat.afgApprovAchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "famille")
@Indexed
public class Famille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_famille")
    int id;

    @FullTextField(analyzer = "french")
    @KeywordField(name = "description_kw", normalizer = "lowercase")
    String description;
    @ManyToOne @JoinColumn(name = "id_sous_famille" , referencedColumnName = "id_famille")
    Famille sousFamille;

    public Famille(String description) {
        this.setDescription(description);
    }
}
