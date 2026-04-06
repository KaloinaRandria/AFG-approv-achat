package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeFilleRepo extends JpaRepository<DemandeFille,Integer> {
    @Query("select dmf from DemandeFille dmf where dmf.demandeMere = :demandeMere")
    List<DemandeFille> findDemandeFilleByDemandeMere(DemandeMere demandeMere);

    @Query("""
    SELECT COALESCE(SUM(l.prixUnitaire * l.quantite), 0)
    FROM DemandeFille l
    WHERE l.demandeMere = :demande
    AND l.statut != :statutRefuse
""")
    double calculerTotal(@Param("demande") DemandeMere demande,
                         @Param("statutRefuse") int statutRefuse);

    @Query("select dmf.article from DemandeFille dmf where dmf.id = :idDemandeFille")
    Article findArticleByIdDemandeFille(int idDemandeFille);

    @Query("SELECT df FROM DemandeFille df WHERE df.demandeMere = :demandeMere AND df.statut = 14")
    List<DemandeFille> findByDemandeMereAndStatutValidee(DemandeMere demandeMere);

    @Query("""
    SELECT df FROM DemandeFille df
    WHERE df.article.codeArticle = :codeArticle
    AND df.prixUnitaire = 0
    """)
    List<DemandeFille> findByArticleCodeAndPrixNull(@Param("codeArticle") String codeArticle);

    Optional<DemandeFille> findByDemandeMereIdAndArticleCodeArticle(
            String demandeId, String codeArticle
    );

    /**
     * Calcule la quantité totale réservée en stock pour un article dans une demande spécifique
     * @param demandeId L'ID de la demande mère
     * @param codeArticle Le code article
     * @return La quantité totale réservée en stock (somme des quantiteStock des lignes STOCK)
     */
    @Query("""
        SELECT COALESCE(SUM(df.quantiteStock), 0)
        FROM DemandeFille df
        WHERE df.demandeMere.id = :demandeId
        AND df.article.codeArticle = :codeArticle
        AND df.typeApprovisionnement = 'STOCK'
        AND df.statut != -1
    """)
    Double getQuantiteStockReserveePourDemande(
            @Param("demandeId") String demandeId,
            @Param("codeArticle") String codeArticle
    );
}
