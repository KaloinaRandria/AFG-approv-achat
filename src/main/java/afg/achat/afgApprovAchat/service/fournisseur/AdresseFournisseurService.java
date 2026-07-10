package afg.achat.afgApprovAchat.service.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.AdresseFournisseur;
import afg.achat.afgApprovAchat.repository.fournisseur.AdresseFournisseurRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdresseFournisseurService {
    private final AdresseFournisseurRepo adresseFournisseurRepo;

    public Optional<AdresseFournisseur> getAdresseActuel(int idFournisseur) {
        return adresseFournisseurRepo.findFirstByFournisseur_IdOrderByDateAffectationDesc(idFournisseur);
    }

    public AdresseFournisseur save(AdresseFournisseur adresseFournisseur) {
         return adresseFournisseurRepo.save(adresseFournisseur);
    }

    public Optional<AdresseFournisseur> getById(int id) {
        return adresseFournisseurRepo.findById(id);
    }
}
