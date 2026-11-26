package afg.achat.afgApprovAchat.model.util;

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
@Table(name = "service", schema = Referentiel.SCHEMA)
public class Service {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_service")
    int id;
    String libelle;
    String acronyme;
}
