package afg.achat.afgApprovAchat.repository.perdiem;

import afg.achat.afgApprovAchat.model.perdiem.ZoneMission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneMissionRepo extends JpaRepository<ZoneMission, Integer> {

    @Query("SELECT z FROM ZoneMission z ORDER BY COALESCE(z.ordre, 999), z.libelle")
    List<ZoneMission> findAllOrdonnees();

    @Query("""
            SELECT z FROM ZoneMission z
            WHERE z.actif IS NULL OR z.actif = true
            ORDER BY COALESCE(z.ordre, 999), z.libelle
            """)
    List<ZoneMission> findActives();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByLibelleIgnoreCase(String libelle);

    boolean existsByLibelleIgnoreCaseAndIdNot(String libelle, Integer id);
}
