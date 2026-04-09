package afg.achat.afgApprovAchat.repository.bonSortie;

import afg.achat.afgApprovAchat.model.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonSortieMereRepo extends JpaRepository<BonSortieMere, Integer> {
    List<BonSortieMere> findByDemandeMere(DemandeMere demandeMere);
    List<BonSortieMere> findByDemandeMereAndStatut(DemandeMere demandeMere,
                                                   BonSortieMere.StatutBonSortie statut);
    // Compte le nombre de BS pour générer le numéro
    long countByDemandeMere(DemandeMere demandeMere);
}
