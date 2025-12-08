package afg.achat.afgApprovAchat.model;

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
@Table(name = "famille")
public class Famille {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_famille")
    int id;
    String description;
    @ManyToOne @JoinColumn(name = "id_sous_famille" , referencedColumnName = "id_famille")
    Famille sousFamille;

    public Famille(String description) {
        this.setDescription(description);
    }
}
