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
@Table(name = "fournisseur")
public class Fournisseur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_fournisseur")
    int id;
    String contact;
    String mail;
    String nom;

    public Fournisseur(String contact, String mail, String nom) {
        this.setContact(contact);
        this.setMail(mail);
        this.setNom(nom);
    }
}
