package afg.achat.afgApprovAchat.repository.perdiem;

import afg.achat.afgApprovAchat.model.perdiem.BaremePerDiem;
import afg.achat.afgApprovAchat.model.perdiem.BaremePerDiem.Statut;
import afg.achat.afgApprovAchat.model.perdiem.TypeIndemnite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BaremePerDiemRepo extends JpaRepository<BaremePerDiem, Integer> {

    /** Taux d'une même ligne du barème (catégorie, zone, type) ayant le statut donné, du plus récent au plus ancien. */
    @Query("""
            SELECT b FROM BaremePerDiem b
            WHERE b.categorie.id = :idCategorie AND b.zone.id = :idZone
              AND b.typeIndemnite = :type AND b.statut = :statut
            ORDER BY b.dateEffet DESC
            """)
    List<BaremePerDiem> findLigne(@Param("idCategorie") Integer idCategorie,
                                  @Param("idZone") Integer idZone,
                                  @Param("type") TypeIndemnite type,
                                  @Param("statut") Statut statut);

    /** Taux validé qui s'applique à la date donnée (au plus un, les périodes ne se chevauchent pas). */
    @Query("""
            SELECT b FROM BaremePerDiem b
            WHERE b.categorie.id = :idCategorie AND b.zone.id = :idZone
              AND b.typeIndemnite = :type AND b.statut = 'VALIDE'
              AND b.dateEffet <= :date AND (b.dateFin IS NULL OR b.dateFin > :date)
            """)
    Optional<BaremePerDiem> findApplicable(@Param("idCategorie") Integer idCategorie,
                                           @Param("idZone") Integer idZone,
                                           @Param("type") TypeIndemnite type,
                                           @Param("date") LocalDate date);

    /** Tous les taux validés applicables à la date donnée (grille du barème). */
    @Query("""
            SELECT b FROM BaremePerDiem b
            WHERE b.statut = 'VALIDE'
              AND b.dateEffet <= :date AND (b.dateFin IS NULL OR b.dateFin > :date)
            """)
    List<BaremePerDiem> findApplicablesLe(@Param("date") LocalDate date);

    /** Taux validés dont la date d'effet est postérieure à la date donnée (programmés). */
    @Query("SELECT b FROM BaremePerDiem b WHERE b.statut = 'VALIDE' AND b.dateEffet > :date ORDER BY b.dateEffet")
    List<BaremePerDiem> findProgrammesApres(@Param("date") LocalDate date);

    @Query("SELECT b FROM BaremePerDiem b WHERE b.statut = :statut ORDER BY b.dateProposition")
    List<BaremePerDiem> findByStatutOrdonnes(@Param("statut") Statut statut);

    /** Historique complet : les plus récents d'abord. */
    @Query("""
            SELECT b FROM BaremePerDiem b
            ORDER BY COALESCE(b.dateTraitement, b.dateProposition) DESC, b.id DESC
            """)
    List<BaremePerDiem> findHistorique();
}
