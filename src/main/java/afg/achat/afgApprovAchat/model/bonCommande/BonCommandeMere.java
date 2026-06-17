package afg.achat.afgApprovAchat.model.bonCommande;

import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bon_commande_mere")
@SequenceGenerator(
        name = "s_bc_mere",
        sequenceName = "s_bc_mere",
        allocationSize = 1
)
public class BonCommandeMere {
    @Id @Column(name = "id_bc_mere")
    String id;
    String numero;
    String description;
    String referenceFournisseur;
    @ManyToOne @JoinColumn(name = "id_fournisseur" , referencedColumnName = "id_fournisseur")
    Fournisseur fournisseur;
    @ManyToOne @JoinColumn(name = "id_createur" , referencedColumnName = "id_utilisateur")
    Utilisateur createur;
    @OneToMany(mappedBy = "bonCommandeMere", cascade = CascadeType.ALL, orphanRemoval = true)
    List<BcContact> contacts = new ArrayList<>();
    LocalDateTime dateCreation;
    LocalDateTime dateLivraisonPrevue;
    String lieuLivraison;
    @ManyToOne @JoinColumn(name = "id_demande_mere" , referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;

    @Column(name = "montant_ht")
    double montantHT;
    @Column(name = "montant_ttc")
    double montantTTC;
    @Column(name = "taux_tva")
    double tauxTVA;
    @Column(name = "remise")
    double remise;



    public void setId(IdGenerator idGenerator) {
        this.id = idGenerator.generateId("BC","s_bc_mere");
    }

    public void ajouterContact(Utilisateur u, BcContact.RoleContact role) {
        BcContact bc = new BcContact();
        bc.setBonCommandeMere(this);
        bc.setUtilisateur(u);
        bc.setRoleContact(role);
        this.contacts.add(bc);
    }

}
