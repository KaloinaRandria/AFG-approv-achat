package afg.achat.afgApprovAchat.model.fournisseur;

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
    String libelle;
}
