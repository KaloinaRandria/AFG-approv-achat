package afg.achat.afgApprovAchat.email;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "mail_password_history")
public class MailPasswordHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String username; // ex: mada_afgbank.alerts@afgbank.mg

    @Column(nullable = false, length = 500)
    private String passwordChiffre; // JAMAIS le mot de passe en clair

    @Column(nullable = false)
    private LocalDateTime dateModification;

    @ManyToOne
    @JoinColumn(name = "modifie_par")
    private Utilisateur modifiePar;

    @Column(nullable = false)
    private boolean actif; // true = mot de passe actuellement utilisé
}
