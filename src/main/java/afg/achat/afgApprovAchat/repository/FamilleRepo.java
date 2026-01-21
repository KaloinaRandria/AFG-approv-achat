package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.Famille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FamilleRepo extends JpaRepository<Famille, Integer> {
    @Query("SELECT f FROM Famille f WHERE f.description = :designation")
    Famille findFamilleByDescription(String designation);
    Boolean existsFamilleByDescriptionIgnoreCase(String designation);
}
