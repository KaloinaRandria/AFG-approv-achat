package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.repository.FournisseurRepo;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FournisseurService {
    @Autowired
    FournisseurRepo fournisseurRepo;

    public Fournisseur[] getAllFournisseurs() {
        return fournisseurRepo.findAll().toArray(new Fournisseur[0]);
    }

    public Optional<Fournisseur> getFournisseurById(int id) {
        return fournisseurRepo.findById(id);
    }

}
