package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BonCommandeService {
    private final BonCommandeMereRepo bonCommandeMereRepo;
    private final BonCommandeFilleRepo bonCommandeFilleRepo;

    public List<BonCommandeFille> getBonCommandeFillesByBonCommandeMere(BonCommandeMere b) {
        return bonCommandeFilleRepo.findBonCommandeFillesByBonCommandeMere(b);
    }
}
