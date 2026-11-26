package afg.achat.afgApprovAchat.model.perdiem;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catégorie de personnel pour le barème des per diem : Directeur, Cadre, Agent…
 *
 * <p>Référentiel tenu par les RH. Une catégorie n'est jamais supprimée : désactivée, elle
 * n'est plus proposée, mais le barème et les missions existantes la conservent.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "categorie_personnel")
public class CategoriePersonnel implements ReferentielMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categorie_personnel")
    Integer id;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    String code;

    @Column(name = "libelle", nullable = false)
    String libelle;

    /** Ordre d'affichage (lignes de la grille du barème). */
    @Column(name = "ordre")
    Integer ordre;

    @Column(name = "actif")
    Boolean actif = true;

    @Override
    public boolean estActif() {
        return actif == null || actif;
    }
}
