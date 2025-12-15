package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.DeviseHistorique;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.util.DeviseRepo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeviseService {
    @Autowired
    DeviseRepo deviseRepo;
    @Autowired
    DeviseHistoriqueService deviseHistoriqueService;

    public Devise[] getAllDevises() {
        return deviseRepo.findAll().toArray(new Devise[0]);
    }

    public void insertDevise(Devise devise) {
        deviseRepo.save(devise);
    }

    public Devise getDeviseById(int id) {
        return deviseRepo.findById(id).orElse(null);
    }

    @Transactional
    public Devise modifierDevise(int idDevise, String coursAriary) {
        Devise devise = this.getDeviseById(idDevise);

        if (devise == null) {
            throw new RuntimeException("Devise non trouvée avec l'id: " + idDevise);
        }

        // Récupérer l'utilisateur courant
        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String utilisateur = user != null ? user.getNom() + " " + user.getPrenom() : "Admin";

        List<DeviseHistorique> historiques = new ArrayList<>();

        if (devise.getCoursAriary() != Double.parseDouble(coursAriary)) {
            DeviseHistorique historique = new DeviseHistorique(
                    devise,
                    String.valueOf(devise.getCoursAriary()),
                    coursAriary,
                    LocalDateTime.now(),
                    utilisateur
            );
            historiques.add(historique);
            devise.setCoursAriary(coursAriary);
            devise.setDateMiseAJour(String.valueOf(LocalDateTime.now()));
        }

        Devise deviseModifiee = devise;
        if (!historiques.isEmpty()) {
            deviseModifiee = deviseRepo.save(deviseModifiee);

            for (DeviseHistorique historique : historiques) {
                deviseHistoriqueService.saveDeviseHistorique(historique);
            }
        }
        return deviseModifiee;
    }
}
