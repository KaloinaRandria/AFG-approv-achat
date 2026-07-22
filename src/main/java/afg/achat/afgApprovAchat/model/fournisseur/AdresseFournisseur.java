package afg.achat.afgApprovAchat.model.fournisseur;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "adresse_fournisseur")
public class AdresseFournisseur {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_adresse_fournisseur")
    int id;
    @ManyToOne @JoinColumn(name = "id_adresse")
    Adresse adresse;
    @ManyToOne @JoinColumn(name = "id_fournisseur")
    Fournisseur fournisseur;
    @Column(name = "date_affectation")
    LocalDate dateAffectation;
}
