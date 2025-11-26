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
@Table(name = "transport")
public class Transport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_transport")
    int id;
    String type;

    public Transport(String type) {
        this.setType(type);
    }
}
