package afg.achat.afgApprovAchat.model.utilisateur;

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
@Table(name = "poste")
public class Poste {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_poste")
    int id;
    String poste;

     public Poste(String poste) {
        this.poste = poste;
    }
}
