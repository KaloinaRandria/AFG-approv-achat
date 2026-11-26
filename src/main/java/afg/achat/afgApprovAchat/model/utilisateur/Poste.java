package afg.achat.afgApprovAchat.model.utilisateur;

import afg.achat.afgApprovAchat.model.Referentiel;
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
@Table(name = "poste", schema = Referentiel.SCHEMA)
public class Poste {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_poste")
    int id;
    String poste;

    /** Direction à laquelle le poste est rattaché ; {@code null} si non rattaché. */
    @ManyToOne
    @JoinColumn(name = "id_service", referencedColumnName = "id_service", nullable = true)
    afg.achat.afgApprovAchat.model.util.Service direction;

     public Poste(String poste) {
        this.poste = poste;
    }
}
