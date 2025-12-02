package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.repository.FamilleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FamilleService {
    @Autowired
    FamilleRepo familleRepo;

    public Famille[] getAllFamilles() {
        return familleRepo.findAll().toArray(new Famille[0]);
    }
}
