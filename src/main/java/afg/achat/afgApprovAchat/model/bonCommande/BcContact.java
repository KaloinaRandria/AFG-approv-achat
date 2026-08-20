package afg.achat.afgApprovAchat.model.bonCommande;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
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
@Table(name = "bc_contact",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"id_bc_mere", "id_utilisateur"}
        )
)
public class BcContact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_bc_contact")
    int id;
    @ManyToOne @JoinColumn(name = "id_bc_mere" , referencedColumnName = "id_bc_mere")
    BonCommandeMere bonCommandeMere;
    @ManyToOne @JoinColumn(name = "id_utilisateur" , referencedColumnName = "id_utilisateur")
    Utilisateur utilisateur;
    @Enumerated(EnumType.STRING) @Column(name = "role_contact")
    RoleContact roleContact;

    public enum RoleContact {
        CONTACT_PRINCIPAL,
        CONTACT_SECONDAIRE
    }
}
