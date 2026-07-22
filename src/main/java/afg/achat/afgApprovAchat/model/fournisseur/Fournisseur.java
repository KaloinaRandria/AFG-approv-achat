package afg.achat.afgApprovAchat.model.fournisseur;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "fournisseur")
public class Fournisseur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_fournisseur")
    int id;
    String contact;
    String mail;
    String nom;
    String acronyme;
    String description;
    String adressePhysique;

    public Fournisseur(String contact, String mail, String nom, String acronyme) {
        this.setContact(contact);
        this.setMail(mail);
        this.setNom(nom);
        this.setAcronyme(acronyme);
    }
}
