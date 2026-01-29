package afg.achat.afgApprovAchat.model.bonLivraison;

import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
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
@SequenceGenerator(
        name = "bon_livraison_mere_id_bl_mere_seq",
        sequenceName = "bon_livraison_mere_id_bl_mere_seq",
        allocationSize = 1
)
public class BonLivraisonMere {
    @Id @Column(name = "id_bl_mere")
    String id;
    String description;
    @Column(name = "date_reception")
    LocalDateTime dateReception;
    @Lob @Column(name = "piece_jointe" , nullable = true)
    byte[] pieceJointe;
    @ManyToOne @JoinColumn(name = "id_fournisseur" , referencedColumnName = "id_fournisseur")
    Fournisseur fournisseur;
    @ManyToOne @JoinColumn(name = "id_devise" ,  referencedColumnName = "id_devise")
    Devise devise;
    @Column(name = "total_prix")
    Double totalPrix;
    @Column(name = "id_facture")
    String idFacture;

    public void setId(IdGenerator idGenerator) {
        this.id = idGenerator.generateId("BL","bon_livraison_mere_id_bl_mere_seq");
    }
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
