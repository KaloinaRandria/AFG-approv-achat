package afg.achat.afgApprovAchat.model.util;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
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
@Table(name = "commentaire_finance")
public class CommentaireFinance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_commentaire_finance")
    int id;
    @ManyToOne @JoinColumn(name = "id_utilisateur", referencedColumnName = "id_utilisateur")
    Utilisateur commentateur;
    @ManyToOne @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;
    @Column(columnDefinition = "TEXT")
    String commentaire;
}
