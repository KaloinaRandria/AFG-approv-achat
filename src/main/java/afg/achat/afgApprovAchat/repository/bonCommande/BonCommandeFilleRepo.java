package afg.achat.afgApprovAchat.repository.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonCommandeFilleRepo extends JpaRepository<BonCommandeFille, Integer> {
    @Query("select bcf from BonCommandeFille bcf where bcf.bonCommandeMere = :b")
    List<BonCommandeFille> findBonCommandeFillesByBonCommandeMere(BonCommandeMere b);
}
