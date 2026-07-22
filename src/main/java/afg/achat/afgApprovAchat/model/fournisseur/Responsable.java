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
@Table(name = "responsable")
public class Responsable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_responsable")
    int id;
    String nom;
    String prenom;
    String contact;
}
