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
@Table(name = "agence")
public class Agence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_agence")
    int id;
    String code;
    String libelle;
}
