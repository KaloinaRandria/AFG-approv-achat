package afg.achat.afgApprovAchat.repository.bonLivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonLivraisonFilleRepo extends JpaRepository<BonLivraisonFille,Integer> {

    @Query("select blf from BonLivraisonFille blf where blf.bonLivraisonMere.id = :idMere")
    List<BonLivraisonFille> findBonLivraisonFilleByBonLivraisonMere(int idMere);
}
