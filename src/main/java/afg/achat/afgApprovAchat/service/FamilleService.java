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

    public Optional<Famille> getFamilleById(Integer id) {
        return familleRepo.findById(id);
    }
}
