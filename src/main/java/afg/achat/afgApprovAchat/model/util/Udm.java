package afg.achat.afgApprovAchat.model.util;

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
@Table(name = "udm")
@Indexed
public class Udm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_udm")
    int id;

    @FullTextField(analyzer = "french")
    String description;
    @KeywordField(normalizer = "lowercase")
    String acronyme;

    public Udm(String description, String acronyme) {
        this.setDescription(description);
        this.setAcronyme(acronyme);
    }
}
