package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.FournisseurHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FournisseurHistoriqueRepo extends JpaRepository<FournisseurHistorique, Integer> {
    @Query("SELECT f FROM FournisseurHistorique f WHERE f.id = :id")
    FournisseurHistorique[] findFournisseurHistoriqueById(int id);
}
