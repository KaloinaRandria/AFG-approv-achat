package afg.achat.afgApprovAchat.service.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.Adresse;
import afg.achat.afgApprovAchat.repository.fournisseur.AdresseRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdresseService {
    private final AdresseRepo adresseRepo;

    public Adresse save(Adresse adresse) {
        return adresseRepo.save(adresse);
    }
}
