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
@Table(name = "consommateur")
public class Consommateur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_consommateur")
    int id;
    String description;
    String rang;

    public  Consommateur(String description, String rang) {
        this.setDescription(description);
        this.setRang(rang);
    }
}
