package afg.achat.afgApprovAchat.model.utilisateur;

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
@Table(name = "utilisateur")
public class Utilisateur {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_utilisateur")
    int id;
    String nom;
    String prenom;
    String mail;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "utilisateur_role",
            joinColumns = @JoinColumn(name = "id_utilisateur"),
            inverseJoinColumns = @JoinColumn(name = "id_role")
    )
    private Set<Role> roles = new HashSet<>();
    @ManyToOne @JoinColumn(name = "id_superieur", referencedColumnName = "id_utilisateur", nullable = true)
    Utilisateur superieurHierarchique;

    public Utilisateur(String nom, String prenom, String mail, Set<Role> roles, Utilisateur superieurHierarchique) {
        this.setNom(nom);
        this.setPrenom(prenom);
        this.setMail(mail);
        this.setRoles(roles);
        this.setSuperieurHierarchique(superieurHierarchique);
    }

}
