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
import java.util.Optional;

@Service
public class FournisseurService {
    @Autowired
    FournisseurRepo fournisseurRepo;

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

    @Transactional
    public Fournisseur modifierFournisseur(int id,
                                           String nom,
                                           String acronyme,
                                           String mail,
                                           String contact,
                                           String description) {

        // 1) Récupérer le fournisseur existant
        Fournisseur fournisseur = this.getFournisseurById(id)
                .orElseThrow(() -> new RuntimeException("Fournisseur non trouvé"));

        // 2) Récupérer l'utilisateur courant
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String utilisateur = "Admin";
        if (principal instanceof Utilisateur user) {
            utilisateur = user.getNom() + " " + user.getPrenom();
        }

        List<FournisseurHistorique> historiques = new ArrayList<>();

        // Helper local pour éviter NPE sur equals
        java.util.function.BiPredicate<String, String> diff = (oldVal, newVal) -> {
            if (newVal == null) return false; // si tu veux autoriser la mise à null, change cette logique
            if (oldVal == null) return !newVal.isEmpty();
            return !newVal.equals(oldVal);
        };

        // 3) Vérifier et mettre à jour chaque champ + historique

        // Nom
        if (diff.test(fournisseur.getNom(), nom)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Nom",
                    fournisseur.getNom(),
                    nom,
                    utilisateur,
                    String.valueOf(fournisseur.getId()) // ou fournisseur.getCode() si tu as un code
            ));
            fournisseur.setNom(nom);
        }

        // Acronyme
        if (diff.test(fournisseur.getAcronyme(), acronyme)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Acronyme",
                    fournisseur.getAcronyme(),
                    acronyme,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setAcronyme(acronyme);
        }

        // Mail
        if (diff.test(fournisseur.getMail(), mail)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Mail",
                    fournisseur.getMail(),
                    mail,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setMail(mail);
        }

        // Contact (téléphone)
        if (diff.test(fournisseur.getContact(), contact)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Contact",
                    fournisseur.getContact(),
                    contact,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setContact(contact);
        }

        // Description
        if (diff.test(fournisseur.getDescription(), description)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Description",
                    fournisseur.getDescription(),
                    description,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setDescription(description);
        }

        // 4) Sauvegarde seulement si modifications
        Fournisseur fournisseurModifie = fournisseur;
        if (!historiques.isEmpty()) {
            fournisseurModifie = fournisseurRepo.save(fournisseur);

            for (FournisseurHistorique h : historiques) {
                fournisseurHistoriqueService.saveFournisseurHistorique(h);
            }
        }

        return fournisseurModifie;
    }

}
