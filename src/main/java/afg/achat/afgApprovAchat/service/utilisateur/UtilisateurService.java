package afg.achat.afgApprovAchat.service.utilisateur;

import afg.achat.afgApprovAchat.DTO.UtilisateurRequestDTO;
import afg.achat.afgApprovAchat.model.utilisateur.Pdp;
import afg.achat.afgApprovAchat.model.utilisateur.Poste;
import afg.achat.afgApprovAchat.model.utilisateur.Role;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.util.RoleRepo;
import afg.achat.afgApprovAchat.repository.util.ServiceRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.PdpRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.PosteRepo;
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
    @Autowired
    PosteRepo posteRepo;
    @Autowired
    ServiceRepo serviceRepo;
    @Autowired
    PdpRepo pdpRepo;
    @Autowired
    RoleRepo roleRepo;

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

    @Transactional
    public Utilisateur insererUtilisateur(UtilisateurRequestDTO dto) {

        // 1. Unicité du mail
        if (utilisateurRepo.findByMail(dto.getMail()) != null) {
            throw new IllegalArgumentException(
                    "Un utilisateur avec le mail '" + dto.getMail() + "' existe déjà."
            );
        }

        // 2. Résolution des rôles
        Set<Role> roles = new HashSet<>();
        if (dto.getRoleIds() != null) {
            for (Integer id : dto.getRoleIds()) {
                roles.add(roleRepo.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable : " + id)));
            }
        }

        // 3. Supérieur hiérarchique (nullable)
        Utilisateur superieur = null;
        if (dto.getSuperieurHierarchiqueId() != null) {
            superieur = utilisateurRepo.findById(dto.getSuperieurHierarchiqueId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Supérieur introuvable : " + dto.getSuperieurHierarchiqueId()));
        }

        // 4. PDP (nullable)
        Pdp pdp = null;
        if (dto.getPdpId() != null) {
            pdp = pdpRepo.findById(dto.getPdpId())
                    .orElseThrow(() -> new IllegalArgumentException("PDP introuvable : " + dto.getPdpId()));
        }

        // 5. Poste (nullable)
        Poste poste = null;
        if (dto.getPosteId() != null) {
            poste = posteRepo.findById(dto.getPosteId())
                    .orElseThrow(() -> new IllegalArgumentException("Poste introuvable : " + dto.getPosteId()));
        }

        // 6. Service (nullable)
        afg.achat.afgApprovAchat.model.util.Service service = null;
        if (dto.getServiceId() != null) {
            service = serviceRepo.findById(dto.getServiceId())
                    .orElseThrow(() -> new IllegalArgumentException("Service introuvable : " + dto.getServiceId()));
        }

        // 7. Construction et première sauvegarde
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(dto.getNom());
        utilisateur.setPrenom(dto.getPrenom());
        utilisateur.setMail(dto.getMail());
        utilisateur.setContact(dto.getContact());
        utilisateur.setRoles(roles);
        utilisateur.setSuperieurHierarchique(superieur);
        utilisateur.setPdp(pdp);
        utilisateur.setPoste(poste);
        utilisateur.setService(service);

        // Premier save → génère l'id nécessaire pour la table utilisateur_validateur
        Utilisateur sauvegarde = utilisateurRepo.save(utilisateur);

        // 8. Validateurs (table utilisateur_validateur, après le 1er save)
        if (dto.getValidateurIds() != null && !dto.getValidateurIds().isEmpty()) {
            Set<Utilisateur> validateurs = new HashSet<>();
            for (Integer valId : dto.getValidateurIds()) {
                if (valId == sauvegarde.getId()) {
                    throw new IllegalArgumentException(
                            "Un utilisateur ne peut pas être son propre validateur.");
                }
                validateurs.add(utilisateurRepo.findById(valId)
                        .orElseThrow(() -> new IllegalArgumentException("Validateur introuvable : " + valId)));
            }
            sauvegarde.setValidateurs(validateurs);
            sauvegarde = utilisateurRepo.save(sauvegarde);
        }

        return sauvegarde;
    }
}
