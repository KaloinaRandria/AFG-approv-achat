package afg.achat.afgApprovAchat.model.bonCommande;

import afg.achat.afgApprovAchat.model.fournisseur.AdresseFournisseur;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.fournisseur.ResponsableFournisseur;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.DocumentEnDevise;
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
public class BonCommandeMere implements DocumentEnDevise {
    @Id @Column(name = "id_bc_mere")
    String id;
    @Column(unique = true)
    String numero;
    String description;
    String referenceFournisseur;
    @ManyToOne @JoinColumn(name = "id_fournisseur" , referencedColumnName = "id_fournisseur")
    Fournisseur fournisseur;
    @ManyToOne
    @JoinColumn(name = "id_responsable_fournisseur", referencedColumnName = "id" )
    private ResponsableFournisseur responsableFournisseur;
    @ManyToOne
    @JoinColumn(name = "id_adresse_fournisseur")
    private AdresseFournisseur adresseFournisseur;
    @ManyToOne @JoinColumn(name = "id_createur" , referencedColumnName = "id_utilisateur")
    Utilisateur createur;
    @OneToMany(mappedBy = "bonCommandeMere", cascade = CascadeType.ALL, orphanRemoval = true)
    List<BcContact> contacts = new ArrayList<>();
    @OneToMany(mappedBy = "bonCommandeMere", cascade = CascadeType.ALL, orphanRemoval = true)
    List<BonCommandeFille> lignes = new ArrayList<>();
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

    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    StatutBonCommande statut;

    // ── Devise : prix et montants du bon sont dans cette devise (ariary par défaut) ──
    @ManyToOne @JoinColumn(name = "id_devise", referencedColumnName = "id_devise")
    Devise devise;
    /** Cours de la devise en ariary, recopié à chaque enregistrement du brouillon puis figé. */
    @Column(name = "taux_change")
    Double tauxChange;
    @Column(name = "date_taux")
    LocalDateTime dateTaux;

    // ── Informations du bon de commande imprimé ─────────────────────────────

    @Column(name = "modalite_paiement")
    String modalitePaiement;

    /** Référence de la décision qui autorise la dépense, ex. « CODEP 007/26 du 06/03/26 ». */
    @Column(name = "reference_approbation")
    String referenceApprobation;

    // ── Validation (Moyens Généraux puis S.G.) ──────────────────────────────

    /** Validateur attendu quand le statut est EN_ATTENTE_VALIDATION ; {@code null} sinon. */
    @Enumerated(EnumType.STRING)
    @Column(name = "etape_validation")
    EtapeValidation etapeValidation;

    @ManyToOne @JoinColumn(name = "id_soumis_par", referencedColumnName = "id_utilisateur")
    Utilisateur soumisPar;
    @Column(name = "date_soumission")
    LocalDateTime dateSoumission;

    @ManyToOne @JoinColumn(name = "id_valide_mg_par", referencedColumnName = "id_utilisateur")
    Utilisateur valideMgPar;
    @Column(name = "date_validation_mg")
    LocalDateTime dateValidationMg;

    @ManyToOne @JoinColumn(name = "id_valide_sg_par", referencedColumnName = "id_utilisateur")
    Utilisateur valideSgPar;
    @Column(name = "date_validation_sg")
    LocalDateTime dateValidationSg;

    /** Motif du dernier rejet ; effacé à la soumission suivante. */
    @Column(name = "motif_rejet", columnDefinition = "TEXT")
    String motifRejet;
    @ManyToOne @JoinColumn(name = "id_rejete_par", referencedColumnName = "id_utilisateur")
    Utilisateur rejetePar;

    public enum StatutBonCommande {
        BROUILLON,
        EN_ATTENTE_VALIDATION,
        VALIDE,
        ANNULE
    }

    // ── Réception (bons de livraison) ───────────────────────────────────────

    /** Avancement de la réception ; {@code null} pour les bons antérieurs = rien reçu. */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_reception")
    StatutReception statutReception;

    /** Motif de la clôture d'un reliquat que le fournisseur ne livrera pas. */
    @Column(name = "motif_cloture_reception", columnDefinition = "TEXT")
    String motifClotureReception;
    @ManyToOne @JoinColumn(name = "id_cloture_reception_par", referencedColumnName = "id_utilisateur")
    Utilisateur clotureReceptionPar;
    @Column(name = "date_cloture_reception")
    LocalDateTime dateClotureReception;

    public enum StatutReception {
        NON_RECU("Non reçu"),
        PARTIELLEMENT_RECU("Partiellement reçu"),
        RECU("Reçu"),
        /** Réception arrêtée avec un reliquat que le fournisseur ne livrera pas. */
        SOLDE("Soldé (reliquat abandonné)");

        private final String libelle;

        StatutReception(String libelle) { this.libelle = libelle; }

        public String getLibelle() { return libelle; }
    }

    /** Statut de réception ; {@code NON_RECU} pour les bons enregistrés avant son introduction. */
    public StatutReception getStatutReception() {
        return statutReception != null ? statutReception : StatutReception.NON_RECU;
    }

    /** Un bon validé dont la réception n'est ni complète ni soldée attend encore des livraisons. */
    public boolean estReceptionnable() {
        StatutReception r = getStatutReception();
        return statut == StatutBonCommande.VALIDE
                && (r == StatutReception.NON_RECU || r == StatutReception.PARTIELLEMENT_RECU);
    }

    /** Étapes de validation d'un bon de commande, dans l'ordre. */
    public enum EtapeValidation {
        MG("Moyens Généraux", "MG_VALIDATOR"),
        SG("Secrétariat Général", "SG");

        private final String libelle;
        private final String role;

        EtapeValidation(String libelle, String role) {
            this.libelle = libelle;
            this.role = role;
        }

        public String getLibelle() { return libelle; }

        /** Rôle (sans préfixe ROLE_) qui valide à cette étape. */
        public String getRole() { return role; }
    }

    /** Bon de commande saisi directement, sans demande d'achat à l'origine. */
    public boolean estManuel() {
        return demandeMere == null;
    }

    /** Libellé du statut, précisé par le validateur attendu. */
    public String getLibelleStatut() {
        if (statut == null) return "—";
        return switch (statut) {
            case BROUILLON -> "Brouillon";
            case EN_ATTENTE_VALIDATION -> etapeValidation != null
                    ? "En attente " + etapeValidation.getLibelle()
                    : "En attente de validation";
            case VALIDE -> "Validé";
            case ANNULE -> "Annulé";
        };
    }

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
