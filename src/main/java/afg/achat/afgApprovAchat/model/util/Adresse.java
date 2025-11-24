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
@Table(name = "adresse")
public class Adresse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_adresse")
    int id;
    String adresse;

    public Adresse(String adresse) {
        this.setAdresse(adresse);
    }
}
