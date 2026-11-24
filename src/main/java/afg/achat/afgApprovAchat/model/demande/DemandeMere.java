package afg.achat.afgApprovAchat.model.demande;

import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.DocumentEnDevise;
import afg.achat.afgApprovAchat.model.util.ModeTraitement;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.util.TypeDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
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
@Table(name = "demande_mere")
@SequenceGenerator(
        name = "demande_mere_id_demande_mere_seq",
        sequenceName = "demande_mere_id_demande_mere_seq",
        allocationSize = 1
)
public class DemandeMere implements DocumentEnDevise {
    @Id
    @Column(name = "id_demande_mere")
    String id;
    @ManyToOne @JoinColumn(name = "id_demandeur", referencedColumnName = "id_utilisateur")
    Utilisateur demandeur;
    @Column(name = "date_demande")
    LocalDateTime dateDemande;
    @Column(name = "date_sortie") //date prevue livraison
    LocalDateTime dateSortie;


    @Enumerated(EnumType.STRING)
    @Column(name = "nature_demande")
    NatureDemande natureDemande;

    /** Détermine le circuit de validation ; {@code null} pour les demandes antérieures (circuit standard). */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_demande")
    TypeDemande typeDemande;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "motif_evoque", columnDefinition = "TEXT")
    String motifEvoque;

    @Enumerated(EnumType.STRING)
    PrioriteDemande priorite;

    @ManyToOne
    @JoinColumn(name = "id_centre_budgetaire", referencedColumnName = "id_centre_budgetaire")
    CentreBudgetaire centreBudgetaire;

    @Column(name = "total_prix")
    Double totalPrix;

    int statut;

    private Boolean viaCodep = false;

    @Enumerated(EnumType.STRING)
    @Column(name="etat_livraison")
    EtatLivraison etatLivraison = EtatLivraison.NON_LIVREE;

    @Column(name = "code_provisoire")
    String codeProvisoire;

    @Column(name = "total_estime")
    Double totalEstime;

    @ManyToOne @JoinColumn(name = "id_mode_traitement", referencedColumnName = "id_mode_traitement")
    ModeTraitement modeTraitement;

    // ── Devise (choisie par les Moyens Généraux à leur étape ; ariary par défaut) ──
    @ManyToOne @JoinColumn(name = "id_devise", referencedColumnName = "id_devise")
    Devise devise;
    /** Cours de la devise en ariary quand elle a été choisie ; figé après l'étape M.G. */
    @Column(name = "taux_change")
    Double tauxChange;
    @Column(name = "date_taux")
    LocalDateTime dateTaux;

    /**
     * Suivi opérationnel après la validation finale. Il ne fait pas partie du
     * workflow de validation : une demande reste donc VALIDEE après son envoi
     * manuel à la finance.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_transmission_finance")
    StatutTransmissionFinance statutTransmissionFinance = StatutTransmissionFinance.NON_CONCERNEE;

    @ManyToOne
    @JoinColumn(name = "id_transmis_finance_par", referencedColumnName = "id_utilisateur")
    Utilisateur transmisFinancePar;

    @Column(name = "date_transmission_finance")
    LocalDateTime dateTransmissionFinance;

    @Column(name = "commentaire_transmission_finance", columnDefinition = "TEXT")
    String commentaireTransmissionFinance;

    @OneToOne(mappedBy = "demande", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private afg.achat.afgApprovAchat.model.paiement.PaiementDirect paiementDirect;


    public enum PrioriteDemande {
        P2,
        P1,
        P0
    }
    public enum NatureDemande {
        OPEX,
        CAPEX
    }

    public enum EtatLivraison {
        NON_LIVREE,       // Aucun BS encore
        EN_COURS_SORTIE,  // Au moins 1 BS confirmé, reste des lignes
        SOLDEE            // Toutes les lignes sorties
    }

    public enum StatutTransmissionFinance {
        NON_CONCERNEE,
        A_TRANSMETTRE,
        TRANSMISE_FINANCE,
        PAYEE
    }

    public void setId(IdGenerator idGenerator) {
        this.id = idGenerator.generateId("DM","demande_mere_id_demande_mere_seq");
    }

    public void setDateDemande(String dateDemande) {
        this.dateDemande = LocalDateTime.parse(dateDemande);
    }
    public void setDateSortie(String dateSortie) {
        this.dateSortie = LocalDateTime.parse(dateSortie);
    }
    public DemandeMere(String dateDemande, String dateSortie) {
        this.setDateDemande(dateDemande);
        this.setDateSortie(dateSortie);
    }

    /** Type utilisé pour le circuit : jamais {@code null}. */
    public TypeDemande getTypeDemandeEffectif() {
        return TypeDemande.ouStandard(typeDemande);
    }

    public boolean getDecisionViaCodep(Boolean isCodepWorkflow) {
        // Si la demande est validée et qu'elle est dans le workflow CODEP
        if (this.statut == StatutDemande.VALIDE && isCodepWorkflow) {
            return true;
        }

        // Ou si le statut indique directement que CODEP a pris la décision
        if (this.statut == StatutDemande.DECISION_CODEP) {
            return true;
        }

        return false;
    }
}
