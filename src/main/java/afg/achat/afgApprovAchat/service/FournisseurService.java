package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.exception.FournisseurAlreadyExistsException;
import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.util.FournisseurHistorique;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.FournisseurRepo;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import afg.achat.afgApprovAchat.service.util.FournisseurHistoriqueService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class FournisseurService {
    @Autowired
    FournisseurRepo fournisseurRepo;
    @Autowired
    FournisseurHistoriqueService fournisseurHistoriqueService;

    public Fournisseur[] getAllFournisseurs() {
        return fournisseurRepo.findAll().toArray(new Fournisseur[0]);
    }

    public Optional<Fournisseur> getFournisseurById(int id) {
        return fournisseurRepo.findById(id);
    }

    public void saveFournisseurIfNotExists(Fournisseur fournisseur) {

        if (fournisseurRepo.existsFournisseurByNomIgnoreCase(fournisseur.getNom())) {
            throw new FournisseurAlreadyExistsException(fournisseur.getNom());
        }

        fournisseurRepo.save(fournisseur);
    }

    private String norm(String s) {
        return s == null ? null : s.trim();
    }

    private boolean changed(String oldVal, String newVal) {
        // Compare en gérant null
        return !Objects.equals(oldVal, newVal);
    }


    @Transactional
    public Fournisseur modifierFournisseur(int id,
                                           String nom,
                                           String acronyme,
                                           String mail,
                                           String contact,
                                           String description) {

        // 1) Récupération du fournisseur existant
        Fournisseur fournisseur = this.getFournisseurById(id)
                .orElseThrow(() -> new RuntimeException("Fournisseur non trouvé"));

        // 2) Utilisateur courant
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String utilisateur = "Admin";
        if (principal instanceof Utilisateur user) {
            utilisateur = user.getNom() + " " + user.getPrenom();
        }

        List<FournisseurHistorique> historiques = new ArrayList<>();

        // 3) Normalisation des nouvelles valeurs
        String newNom         = norm(nom);
        String newAcronyme    = norm(acronyme);
        String newMail        = norm(mail);
        String newContact     = norm(contact);
        String newDescription = norm(description);

        // 4) Comparaison + historique + mise à jour

        // Nom
        if (newNom != null && changed(fournisseur.getNom(), newNom)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Nom",
                    fournisseur.getNom(),
                    newNom,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setNom(newNom);
        }

        // Acronyme
        if (newAcronyme != null && changed(fournisseur.getAcronyme(), newAcronyme)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Acronyme",
                    fournisseur.getAcronyme(),
                    newAcronyme,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setAcronyme(newAcronyme);
        }

        // Mail
        if (newMail != null && changed(fournisseur.getMail(), newMail)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Mail",
                    fournisseur.getMail(),
                    newMail,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setMail(newMail);
        }

        // Contact
        if (newContact != null && changed(fournisseur.getContact(), newContact)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Contact",
                    fournisseur.getContact(),
                    newContact,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setContact(newContact);
        }

        // Description (peut être null)
        if (changed(fournisseur.getDescription(), newDescription)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Description",
                    fournisseur.getDescription(),
                    newDescription,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setDescription(newDescription);
        }

        // 5) Sauvegarde uniquement s'il y a des modifications
        if (!historiques.isEmpty()) {
            fournisseur = fournisseurRepo.save(fournisseur);
            for (FournisseurHistorique h : historiques) {
                fournisseurHistoriqueService.saveFournisseurHistorique(h);
            }
        }

        return fournisseur;
    }


}
