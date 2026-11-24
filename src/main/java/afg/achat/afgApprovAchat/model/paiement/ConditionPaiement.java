package afg.achat.afgApprovAchat.model.paiement;

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
@Table(name = "condition_paiement")
public class ConditionPaiement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_condition_paiement")
    int id;
    String libelle;

    @Enumerated(EnumType.STRING)
    @Column(name = "declencheur_acompte")
    DeclencheurPaiement declencheurAcompte;

    @Column(name = "pct_acompte")
    int pctAcompte; //pourcentage au moment du signature du BC
    @Column(name = "pct_livraison")
    int pctLivraison; //pourcentage au moment de la reception BL
    @Column(name = "pct_apres_livraison")
    int pctApresLivraison;

    @Column(name = "delai_jours")
    int delaiJours; //nb jours apres la livraison pour payer la tranche

    // Dans un fichier séparé : model/util/DeclencheurPaiement.java
    public enum DeclencheurPaiement {
        SIGNATURE_BC,       // dès la validation et signature du bon de commande
        RECEPTION_BL       // à la réception et validation du bon de livraison
    }
}
