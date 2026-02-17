package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandeFilleRepo extends JpaRepository<DemandeFille,Integer> {
    @Query("select dmf from DemandeFille dmf where dmf.demandeMere = :demandeMere")
    List<DemandeFille> findDemandeFilleByDemandeMere(DemandeMere demandeMere);
    @Query("""
    SELECT COALESCE(SUM(l.article.prixUnitaire * l.quantite), 0)
    FROM DemandeFille l
    WHERE l.demandeMere = :demande
    AND l.statut != :statutRefuse
""")
    double calculerTotal(@Param("demande") DemandeMere demande,
                         @Param("statutRefuse") int statutRefuse);

    @Query("select dmf.article from DemandeFille dmf where dmf.id = :idDemandeFille")
    Article findArticleByIdDemandeFille(int idDemandeFille);

}
