package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.CategorieDepense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategorieDepenseRepo extends JpaRepository<CategorieDepense, Integer> {

    /** Catégories proposées à la saisie (les catégories sans indicateur sont actives). */
    @Query("""
            SELECT c FROM CategorieDepense c
            WHERE c.actif IS NULL OR c.actif = true
            ORDER BY COALESCE(c.ordre, 999), c.libelle
            """)
    List<CategorieDepense> findActives();
}
