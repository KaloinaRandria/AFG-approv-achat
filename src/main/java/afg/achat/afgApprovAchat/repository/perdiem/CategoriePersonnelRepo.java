package afg.achat.afgApprovAchat.repository.perdiem;

import afg.achat.afgApprovAchat.model.perdiem.CategoriePersonnel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriePersonnelRepo extends JpaRepository<CategoriePersonnel, Integer> {

    @Query("SELECT c FROM CategoriePersonnel c ORDER BY COALESCE(c.ordre, 999), c.libelle")
    List<CategoriePersonnel> findAllOrdonnees();

    @Query("""
            SELECT c FROM CategoriePersonnel c
            WHERE c.actif IS NULL OR c.actif = true
            ORDER BY COALESCE(c.ordre, 999), c.libelle
            """)
    List<CategoriePersonnel> findActives();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByLibelleIgnoreCase(String libelle);

    boolean existsByLibelleIgnoreCaseAndIdNot(String libelle, Integer id);
}
