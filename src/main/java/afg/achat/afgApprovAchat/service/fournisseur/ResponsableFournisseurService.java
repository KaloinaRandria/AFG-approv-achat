package afg.achat.afgApprovAchat.service.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.ResponsableFournisseur;
import afg.achat.afgApprovAchat.repository.fournisseur.ResponsableFournisseurRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResponsableFournisseurService {
    private final ResponsableFournisseurRepo responsableFournisseurRepo;

    public Optional<ResponsableFournisseur> getResponsableActuel(int idFournisseur) {
        return responsableFournisseurRepo
                .findFirstByFournisseur_IdOrderByDateAffectationDesc(idFournisseur);
    }

    public Optional<ResponsableFournisseur> getById(int id) {
        return responsableFournisseurRepo.findById(id);
    }

    public ResponsableFournisseur save(ResponsableFournisseur responsableFournisseur) {
        return responsableFournisseurRepo.save(responsableFournisseur);
    }
}
