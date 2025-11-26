package afg.achat.afgApprovAchat.model.utilisateur;

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
@Table(name = "utilisateur")
public class Utilisateur {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_utilisateur")
    int id;
    String nom;
    String prenom;
    String mail;
    @ManyToOne @JoinColumn(name = "id_role", referencedColumnName = "id_role")
    Role role;
    @ManyToOne @JoinColumn(name = "id_superieur", referencedColumnName = "id_utilisateur", nullable = true)
    Utilisateur superieurHierarchique;

    public Utilisateur(String nom, String prenom, String mail, Role role, Utilisateur superieurHierarchique) {
        this.setNom(nom);
        this.setPrenom(prenom);
        this.setMail(mail);
        this.setRole(role);

    }

}
