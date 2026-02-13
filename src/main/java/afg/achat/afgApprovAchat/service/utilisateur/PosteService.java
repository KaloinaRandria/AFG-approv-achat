package afg.achat.afgApprovAchat.service.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Poste;
import afg.achat.afgApprovAchat.repository.utilisateur.PosteRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PosteService {
    @Autowired
    PosteRepo posteRepo;

    public void insertPoste(Poste poste) {
        posteRepo.save(poste);
    }
}
