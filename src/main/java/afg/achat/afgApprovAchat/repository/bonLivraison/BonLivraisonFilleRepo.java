package afg.achat.afgApprovAchat.repository.bonLivraison;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BonLivraisonFilleRepo extends JpaRepository<BonLivraisonFille,Integer> {

    @Query("select blf from BonLivraisonFille blf where blf.bonLivraisonMere.id = :idMere")
    List<BonLivraisonFille> findBonLivraisonFilleByBonLivraisonMere(String idMere);

    boolean existsByBonLivraisonMereAndArticle(BonLivraisonMere bl, Article article);
    Optional<BonLivraisonFille> findByBonLivraisonMereAndArticle(BonLivraisonMere bl, Article article);

}
