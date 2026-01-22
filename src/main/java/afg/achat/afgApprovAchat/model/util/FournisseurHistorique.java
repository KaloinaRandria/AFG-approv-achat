package afg.achat.afgApprovAchat.model.util;


import afg.achat.afgApprovAchat.model.Fournisseur;
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
@Table(name = "fournisseur_historique")
public class FournisseurHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "id_fournisseur", referencedColumnName = "id_fournisseur", nullable = false)
    private Fournisseur fournisseur;

    @Column(name = "champ_modifie", nullable = false)
    private String champModifie;

    @Column(name = "ancienne_valeur")
    private String ancienneValeur;

    @Column(name = "nouvelle_valeur")
    private String nouvelleValeur;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @Column(name = "modifie_par")
    private String modifiePar; // Nom ou email de l'utilisateur

    @Column(name = "code_fournisseur")
    private String codeFournisseur;

    /**
     * Constructeur pratique pour créer un historique automatiquement
     */
    public FournisseurHistorique(Fournisseur fournisseur,
                                 String champModifie,
                                 String ancienneValeur,
                                 String nouvelleValeur,
                                 String modifiePar,
                                 String codeFournisseur) {
        this.fournisseur = fournisseur;
        this.champModifie = champModifie;
        this.ancienneValeur = ancienneValeur;
        this.nouvelleValeur = nouvelleValeur;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = modifiePar;
        this.codeFournisseur = codeFournisseur;
    }
}

