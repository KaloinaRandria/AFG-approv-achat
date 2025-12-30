package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.Adresse;
import afg.achat.afgApprovAchat.repository.util.AdresseRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdresseService {
    @Autowired
    AdresseRepo adresseRepo;

    public Adresse[] getAllAdresses() {
        return adresseRepo.findAll().toArray(new Adresse[0]);
    }

    public Optional<Adresse> getAdresseById(int id) {
        return adresseRepo.findById(id);
    }
}
