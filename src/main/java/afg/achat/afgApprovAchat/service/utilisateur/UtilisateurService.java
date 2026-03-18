package afg.achat.afgApprovAchat.service.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.utilisateur.UtilisateurRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UtilisateurService {
    @Autowired
    UtilisateurRepo utilisateurRepo;

    public Utilisateur getUtilisateurByMail(String mail) {
        return utilisateurRepo.findByMail(mail);
    }

    public List<Integer> getIdsUtilisateurVisible(int userId) {
        List<Integer> ids = new ArrayList<>();
        ids.add(userId);

        // enfants directs
        List<Integer> enfants = utilisateurRepo.findIdsBySuperieur(userId);
        ids.addAll(enfants);

        return ids;
    }

    public List<Utilisateur> getUtilisateursByRole(String roleLibelle) {
        return utilisateurRepo.findByRoleLibelle(roleLibelle);
    }
}
