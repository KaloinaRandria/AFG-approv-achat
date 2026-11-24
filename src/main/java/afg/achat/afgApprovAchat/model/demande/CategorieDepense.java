package afg.achat.afgApprovAchat.model.demande;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catégorie d'une demande Autre (hors matériel, payée en paiement direct) :
 * sponsoring, remboursement, indemnité de repas, lot / récompense, droits divers / honoraires…
 *
 * <p>Référentiel en base : ajouter une catégorie ne demande pas de développement.
 * Une catégorie inactive reste affichée sur les demandes existantes mais n'est plus proposée.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "categorie_depense")
public class CategorieDepense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categorie_depense")
    Integer id;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    String code;

    @Column(name = "libelle", nullable = false)
    String libelle;

    /** Le bénéficiaire (personne ou organisme payé) doit être saisi. */
    @Column(name = "beneficiaire_obligatoire")
    Boolean beneficiaireObligatoire = true;

    @Column(name = "actif")
    Boolean actif = true;

    /** Ordre d'affichage dans la liste de choix. */
    @Column(name = "ordre")
    Integer ordre;

    public boolean estActive() {
        return actif == null || actif;
    }

    public boolean estBeneficiaireObligatoire() {
        return beneficiaireObligatoire == null || beneficiaireObligatoire;
    }
}
