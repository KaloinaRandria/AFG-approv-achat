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
@Table(name = "udm")
public class Udm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_udm")
    int id;
    String description;
    String acronyme;
}
