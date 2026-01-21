package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.repository.FamilleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FamilleService {
    @Autowired
    FamilleRepo familleRepo;

    public Famille[] getAllFamilles() {
        return familleRepo.findAll().toArray(new Famille[0]);
    }

    public Optional<Famille> getFamilleById(int id) {
        return familleRepo.findById(id);
    }

    public Famille getFamilleByDesc(String desc) {
        return familleRepo.findFamilleByDescription(desc);
    }

    public void saveFamille(Famille famille) {
        familleRepo.save(famille);
    }


        public void saveFamilleIfNotExists(String description) {
            if (!familleRepo.existsFamilleByDescriptionIgnoreCase(description)) {
                Famille famille = new Famille();
                famille.setDescription(description.trim());
                familleRepo.save(famille);
            }
        }

}
