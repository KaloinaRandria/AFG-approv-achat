package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.CodepPieceJointe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodepPieceJointeRepo extends JpaRepository<CodepPieceJointe, Integer> {
    List<CodepPieceJointe> findByDemandeMere_IdOrderByUploadedAtDesc(String demandeId);
}
