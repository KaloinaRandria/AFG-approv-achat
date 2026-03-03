package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandePieceJointe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface DemandePieceJointeRepo extends JpaRepository<DemandePieceJointe, Integer> {
    List<DemandePieceJointe> findByDemande_IdOrderByUploadedAtDesc(String demandeId);
}

