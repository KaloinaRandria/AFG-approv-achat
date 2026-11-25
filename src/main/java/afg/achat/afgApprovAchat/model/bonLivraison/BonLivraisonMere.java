package afg.achat.afgApprovAchat.model.bonLivraison;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
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

    /** Bon de commande réceptionné par ce BL ; {@code null} pour un BL saisi sans bon de commande. */
    @ManyToOne @JoinColumn(name = "id_bc_mere", referencedColumnName = "id_bc_mere")
    BonCommandeMere bonCommandeMere;

    /**
     * {@link #totalPrix} en ariary, recalculé à chaque enregistrement : au cours figé du bon de
     * commande réceptionné (comme la valorisation du stock), sinon au cours de la devise du BL.
     */
    @Column(name = "total_prix_ariary")
    Double totalPrixAriary;

    @PrePersist
    @PreUpdate
    void calculerContreValeurAriary() {
        if (totalPrix == null) {
            totalPrixAriary = null;
        } else if (bonCommandeMere != null) {
            totalPrixAriary = bonCommandeMere.contreValeurAriary(totalPrix);
        } else {
            boolean ariary = devise == null || devise.getAcronyme() == null
                    || Devise.CODE_REFERENCE.equalsIgnoreCase(devise.getAcronyme());
            double cours = ariary || devise.getCoursAriary() <= 0 ? 1 : devise.getCoursAriary();
            totalPrixAriary = Math.round(totalPrix * cours * 100) / 100.0;
        }
    }

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
