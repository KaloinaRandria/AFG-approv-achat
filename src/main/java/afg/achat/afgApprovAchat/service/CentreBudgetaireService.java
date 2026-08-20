package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.repository.CentreBudgetaireRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CentreBudgetaireService {

    private final CentreBudgetaireRepo centreBudgetaireRepo;

    public CentreBudgetaire[] getAllCentreBudgetaires() {
        return centreBudgetaireRepo.findAll().toArray(new CentreBudgetaire[0]);
    }

    public CentreBudgetaire getCentreBudgetaireById(int id) {
        return centreBudgetaireRepo.findById(id).orElse(null);
    }

    public void insertCentreBudgetaire(CentreBudgetaire centreBudgetaire) {
        if (centreBudgetaire.getCodeCentre() == null || centreBudgetaire.getCodeCentre().isBlank()) {
            throw new IllegalArgumentException("Le code de la ligne budgétaire est obligatoire.");
        }
        if (centreBudgetaire.getDescription() == null || centreBudgetaire.getDescription().isBlank()) {
            throw new IllegalArgumentException("La description de la ligne budgétaire est obligatoire.");
        }

        String codeCentre = centreBudgetaire.getCodeCentre().trim().toUpperCase();
        String description = centreBudgetaire.getDescription().trim();

        if (centreBudgetaireRepo.existsByCodeCentre(codeCentre)) {
            throw new IllegalArgumentException("Une ligne budgétaire avec ce code existe déjà.");
        }

        centreBudgetaire.setCodeCentre(codeCentre);
        centreBudgetaire.setDescription(description);
        this.centreBudgetaireRepo.save(centreBudgetaire);
    }


}
