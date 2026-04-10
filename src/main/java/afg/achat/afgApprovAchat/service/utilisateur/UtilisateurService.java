package afg.achat.afgApprovAchat.service.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.utilisateur.UtilisateurRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public Utilisateur getUtilisateurById(int id) {
        return utilisateurRepo.findById(id).orElse(null);
    }

    /**
     * Récupère tous les utilisateurs qu'un validateur doit valider
     */
    @Transactional(readOnly = true)
    public List<Utilisateur> getUtilisateursAValider(Integer validateurId) {
        return utilisateurRepo.findUtilisateursAValiderByValidateurId(validateurId);
    }

    /**
     * Récupère les IDs des utilisateurs qu'un validateur doit valider
     */
    @Transactional(readOnly = true)
    public List<Integer> getIdsUtilisateursAValider(Integer validateurId) {
        return utilisateurRepo.findIdsUtilisateursAValiderByValidateurId(validateurId);
    }

    /**
     * Récupère tous les IDs visibles par un utilisateur (hiérarchie + validateurs assignés)
     */
    @Transactional(readOnly = true)
    public List<Integer> getAllVisibleIds(Integer utilisateurId) {
        Set<Integer> allIds = new HashSet<>();

        // IDs hiérarchiques
        allIds.addAll(getIdsUtilisateurVisible(utilisateurId));

        // IDs des utilisateurs à valider
        allIds.addAll(getIdsUtilisateursAValider(utilisateurId));

        return new ArrayList<>(allIds);
    }

    /**
     * Vérifie si un utilisateur est validateur d'un autre utilisateur
     */
    public boolean estValidateurDe(Integer validateurId, Integer utilisateurId) {
        List<Integer> idsAValider = getIdsUtilisateursAValider(validateurId);
        return idsAValider.contains(utilisateurId);
    }
}
