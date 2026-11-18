package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public List<BonCommandeMere> getBonCommandesByDemande(DemandeMere demandeMere) {
        return bonCommandeMereRepo.findByDemandeMereOrderByDateCreationDesc(demandeMere);
    }

    public void deleteBonCommandeFillesByBonCommandeMere(BonCommandeMere bonCommandeMere) {
        bonCommandeFilleRepo.deleteByBonCommandeMere(bonCommandeMere);
    }

    /**
     * Bons de commande d'une demande, du plus récent au plus ancien : ceux créés depuis la
     * demande et les bons groupés qui contiennent au moins une de ses lignes.
     */
    public List<BonCommandeMere> getBonsCommandeDeLaDemande(DemandeMere demande) {
        Map<String, BonCommandeMere> bons = new LinkedHashMap<>();
        bonCommandeMereRepo.findByDemandeMereOrderByDateCreationDesc(demande).forEach(bc -> bons.put(bc.getId(), bc));
        bonCommandeFilleRepo.bonsContenantLaDemande(demande).forEach(bc -> bons.putIfAbsent(bc.getId(), bc));
        return bons.values().stream()
                .sorted(Comparator.comparing(BonCommandeMere::getDateCreation,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Lignes des bons de commande non annulés qui portent sur les lignes d'une demande. */
    public List<BonCommandeFille> getLignesCommandeesDeLaDemande(DemandeMere demande) {
        return bonCommandeFilleRepo.lignesCommandeesDeLaDemande(demande);
    }

    public List<BonCommandeMere> getAllBonCommandes() {
        return bonCommandeMereRepo.findAllByOrderByDateCreationDesc();
    }

    public Optional<BonCommandeMere> getBonCommandeMereById(String id) {
        return bonCommandeMereRepo.findById(id);
    }
}
