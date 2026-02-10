package afg.achat.afgApprovAchat.repository.demande.bonSortie;

import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonSortieFilleRepo extends JpaRepository<BonSortieFille, Integer> {
    List<BonSortieFille> findByBonSortieMere(BonSortieMere bonSortieMere);


}
