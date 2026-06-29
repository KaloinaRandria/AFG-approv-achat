package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BonCommandeService {
    private final BonCommandeMereRepo bonCommandeMereRepo;
    private final BonCommandeFilleRepo bonCommandeFilleRepo;
    private final IdGenerator idGenerator;

    public List<BonCommandeFille> getBonCommandeFillesByBonCommandeMere(BonCommandeMere b) {
        return bonCommandeFilleRepo.findBonCommandeFillesByBonCommandeMere(b);
    }

    public BonCommandeMere saveBonCommandeMere(BonCommandeMere bonCommandeMere) {
        // Générer l'ID si non défini
        if (bonCommandeMere.getId() == null || bonCommandeMere.getId().isEmpty()) {
            bonCommandeMere.setId(idGenerator);
        }
        return bonCommandeMereRepo.save(bonCommandeMere);
    }

    public BonCommandeFille saveBonCommandeFille(BonCommandeFille bonCommandeFille) {
        return bonCommandeFilleRepo.save(bonCommandeFille);
    }

    public Optional<BonCommandeMere> getBonCommandeMereById(String id) {
        return bonCommandeMereRepo.findById(id);
    }
}
