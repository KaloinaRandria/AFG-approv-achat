package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.CodepPieceJointe;
import afg.achat.afgApprovAchat.repository.demande.CodepPieceJointeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CodepPieceJointeService {
    @Autowired
    CodepPieceJointeRepo codepPieceJointeRepo;

    public CodepPieceJointe insert(CodepPieceJointe pj) {
        return codepPieceJointeRepo.save(pj);
    }

    public List<CodepPieceJointe> listByDemandeId(String demandeId) {
        return codepPieceJointeRepo.findByDemandeMere_IdOrderByUploadedAtDesc(demandeId);
    }

    public Optional<CodepPieceJointe> getById(int id) {
        return codepPieceJointeRepo.findById(id);
    }
}
