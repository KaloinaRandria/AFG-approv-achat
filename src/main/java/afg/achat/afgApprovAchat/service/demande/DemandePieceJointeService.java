package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandePieceJointe;
import afg.achat.afgApprovAchat.repository.demande.DemandePieceJointeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DemandePieceJointeService {

    @Autowired
    DemandePieceJointeRepo repo;

    public DemandePieceJointe insert(DemandePieceJointe pj) {
        return repo.save(pj);
    }

    public List<DemandePieceJointe> listByDemandeId(String demandeId) {
        return repo.findByDemande_IdOrderByUploadedAtDesc(demandeId);
    }

    public Optional<DemandePieceJointe> getById(int id) {
        return repo.findById(id);
    }
}
