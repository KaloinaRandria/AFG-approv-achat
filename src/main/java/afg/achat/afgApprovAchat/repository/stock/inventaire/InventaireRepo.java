package afg.achat.afgApprovAchat.repository.stock.inventaire;

import afg.achat.afgApprovAchat.model.stock.inventaire.Inventaire;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventaireRepo extends JpaRepository<Inventaire, Integer>, JpaSpecificationExecutor<Inventaire> {

    List<Inventaire> findByStatutInOrderByDateOuvertureAsc(Collection<Inventaire.Statut> statuts);

    long countByStatut(Inventaire.Statut statut);

    /** Verrou de l'inventaire pendant une décision : deux validations simultanées sont impossibles. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventaire i WHERE i.id = :id")
    Optional<Inventaire> verrouiller(@Param("id") int id);
}
