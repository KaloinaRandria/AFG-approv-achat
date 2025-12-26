package afg.achat.afgApprovAchat.model.bonLivraison;

import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.util.Devise;
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
@Table(name = "bon_livraison_mere")
public class BonLivraisonMere {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_bl_mere")
    int id;
    String description;
    @Column(name = "date_reception")
    LocalDateTime dateReception;
    @Lob @Column(name = "piece_jointe" , nullable = true)
    byte[] pieceJointe;
    @ManyToOne @JoinColumn(name = "id_fournisseur" , referencedColumnName = "id_fournisseur")
    Fournisseur fournisseur;
    @ManyToOne @JoinColumn(name = "id_devise" ,  referencedColumnName = "id_devise")
    Devise devise;

    public void setDateReception(String dateReception) {
        this.dateReception = LocalDateTime.parse(dateReception);
    }

    public BonLivraisonMere(String description, String dateReception, Fournisseur fournisseur,  Devise devise, byte[] pieceJointe) {
        this.setDescription(description);
        this.setDateReception(dateReception);
        this.setFournisseur(fournisseur);
        this.setDevise(devise);
        this.setPieceJointe(pieceJointe);
    }
}
