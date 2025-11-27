package afg.achat.afgApprovAchat.model.util;

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
@Table(name = "local")
public class Local {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_local")
    int id;
    String libelle;

    public Local(String libelle) {
        this.setLibelle(libelle);
    }
}
