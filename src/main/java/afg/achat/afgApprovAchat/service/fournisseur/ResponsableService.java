package afg.achat.afgApprovAchat.service.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.Responsable;
import afg.achat.afgApprovAchat.repository.fournisseur.ResponsableRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResponsableService {
    private final ResponsableRepo responsableRepo;

    public Responsable save(Responsable responsable) {
        return responsableRepo.save(responsable);
    }
}
