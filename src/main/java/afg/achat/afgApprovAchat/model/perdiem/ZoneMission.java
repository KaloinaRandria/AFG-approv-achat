package afg.achat.afgApprovAchat.model.perdiem;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Zone de destination d'une mission pour le barème des per diem : Antananarivo, chef-lieu de
 * province, autre localité, étranger (Afrique, Europe…).
 *
 * <p>Référentiel tenu par les RH. Une zone n'est jamais supprimée : désactivée, elle n'est plus
 * proposée, mais le barème et les missions existantes la conservent.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "zone_mission")
public class ZoneMission implements ReferentielMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_zone_mission")
    Integer id;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    String code;

    @Column(name = "libelle", nullable = false)
    String libelle;

    /** Zone hors de Madagascar : le barème y est souvent en devise. */
    @Column(name = "etranger")
    Boolean etranger = false;

    /** Ordre d'affichage (colonnes de la grille du barème). */
    @Column(name = "ordre")
    Integer ordre;

    @Column(name = "actif")
    Boolean actif = true;

    @Override
    public boolean estActif() {
        return actif == null || actif;
    }

    public boolean estEtranger() {
        return etranger != null && etranger;
    }
}
