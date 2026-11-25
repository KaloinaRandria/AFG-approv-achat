package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * Lignes des demandes de la période (hors brouillons et lignes refusées) dont le mode
     * d'approvisionnement est fixé : [STOCK ou ACHAT, nombre de lignes].
     */
    @Query("""
    SELECT l.typeApprovisionnement, COUNT(l)
    FROM DemandeFille l
    WHERE l.demandeMere.dateDemande BETWEEN :du AND :au
      AND l.demandeMere.statut <> 2
      AND l.statut <> -1
      AND l.typeApprovisionnement IS NOT NULL
    GROUP BY l.typeApprovisionnement
""")
    List<Object[]> lignesParTypeApprovisionnement(@Param("du") java.time.LocalDateTime du,
                                                  @Param("au") java.time.LocalDateTime au);

    /**
     * Lignes à acheter : lignes non refusées des demandes de matériel validées, hors lignes
     * servies depuis le stock (scindées en type STOCK), dont le reste à commander est positif.
     * Reste = quantité demandée − sorties confirmées − commandes non annulées (voir BesoinAchatService).
     * Les anciennes demandes (type STANDARD ou sans type) ne sont pas reprises.
     * Chaque filtre texte vide est ignoré ; la période porte sur la date de la demande.
     */
    @Query(value = """
    SELECT df FROM DemandeFille df
    JOIN df.demandeMere dm
    LEFT JOIN dm.demandeur u
    LEFT JOIN df.article a
    WHERE dm.statut = :valide
      AND dm.typeDemande = afg.achat.afgApprovAchat.model.util.TypeDemande.FOURNITURE
      AND df.statut <> :refuse
      AND df.typeApprovisionnement <> afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement.STOCK
      AND df.quantite
          - COALESCE((SELECT SUM(bs.quantiteSortie) FROM BonSortieFille bs
                      WHERE bs.demandeFille = df AND bs.bonSortieMere.statut = 'CONFIRME'), 0)
          - COALESCE((SELECT SUM(bc.quantiteCommandee) FROM BonCommandeFille bc
                      WHERE bc.demandeFille = df
                        AND bc.bonCommandeMere.statut <> afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande.ANNULE), 0)
          > 0.0001
      AND dm.dateDemande BETWEEN :du AND :au
      AND (:demande = '' OR LOWER(dm.id) LIKE LOWER(CONCAT('%', :demande, '%')))
      AND (:demandeur = '' OR LOWER(CONCAT(COALESCE(u.nom, ''), ' ', COALESCE(u.prenom, '')))
                              LIKE LOWER(CONCAT('%', :demandeur, '%')))
      AND (:article = '' OR LOWER(COALESCE(a.codeArticle, '')) LIKE LOWER(CONCAT('%', :article, '%'))
                         OR LOWER(COALESCE(a.designation, '')) LIKE LOWER(CONCAT('%', :article, '%')))
      AND (:motif = '' OR LOWER(COALESCE(dm.motifEvoque, '')) LIKE LOWER(CONCAT('%', :motif, '%')))
    ORDER BY dm.dateDemande ASC, df.id ASC
""",
    countQuery = """
    SELECT COUNT(df) FROM DemandeFille df
    JOIN df.demandeMere dm
    LEFT JOIN dm.demandeur u
    LEFT JOIN df.article a
    WHERE dm.statut = :valide
      AND dm.typeDemande = afg.achat.afgApprovAchat.model.util.TypeDemande.FOURNITURE
      AND df.statut <> :refuse
      AND df.typeApprovisionnement <> afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement.STOCK
      AND df.quantite
          - COALESCE((SELECT SUM(bs.quantiteSortie) FROM BonSortieFille bs
                      WHERE bs.demandeFille = df AND bs.bonSortieMere.statut = 'CONFIRME'), 0)
          - COALESCE((SELECT SUM(bc.quantiteCommandee) FROM BonCommandeFille bc
                      WHERE bc.demandeFille = df
                        AND bc.bonCommandeMere.statut <> afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande.ANNULE), 0)
          > 0.0001
      AND dm.dateDemande BETWEEN :du AND :au
      AND (:demande = '' OR LOWER(dm.id) LIKE LOWER(CONCAT('%', :demande, '%')))
      AND (:demandeur = '' OR LOWER(CONCAT(COALESCE(u.nom, ''), ' ', COALESCE(u.prenom, '')))
                              LIKE LOWER(CONCAT('%', :demandeur, '%')))
      AND (:article = '' OR LOWER(COALESCE(a.codeArticle, '')) LIKE LOWER(CONCAT('%', :article, '%'))
                         OR LOWER(COALESCE(a.designation, '')) LIKE LOWER(CONCAT('%', :article, '%')))
      AND (:motif = '' OR LOWER(COALESCE(dm.motifEvoque, '')) LIKE LOWER(CONCAT('%', :motif, '%')))
""")
    Page<DemandeFille> rechercherLignesAAcheter(@Param("valide") int valide,
                                               @Param("refuse") int refuse,
                                               @Param("du") LocalDateTime du,
                                               @Param("au") LocalDateTime au,
                                               @Param("demande") String demande,
                                               @Param("demandeur") String demandeur,
                                               @Param("article") String article,
                                               @Param("motif") String motif,
                                               Pageable pageable);

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
     * Quantité des lignes servies depuis le stock (type STOCK) d'un article, pour les demandes
     * de matériel non refusées, hors lignes exclues. Avec {@link #sommeSortiesLignesStock}, donne
     * le stock réservé : ce qui a été promis mais pas encore sorti.
     * Les anciennes demandes (type STANDARD ou sans type) ne réservent rien : elles ne font pas
     * de bon de sortie.
     */
    @Query("""
    SELECT COALESCE(SUM(df.quantite), 0)
    FROM DemandeFille df JOIN df.demandeMere dm
    WHERE df.article.codeArticle = :codeArticle
      AND df.typeApprovisionnement = afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement.STOCK
      AND df.statut <> :refuse
      AND dm.statut <> :refuse
      AND dm.typeDemande = afg.achat.afgApprovAchat.model.util.TypeDemande.FOURNITURE
      AND df.id NOT IN :exclues
""")
    double sommeQuantiteLignesStock(@Param("codeArticle") String codeArticle,
                                    @Param("refuse") int refuse,
                                    @Param("exclues") Collection<Integer> exclues);

    /** Sorties confirmées des lignes comptées par {@link #sommeQuantiteLignesStock}. */
    @Query("""
    SELECT COALESCE(SUM(bs.quantiteSortie), 0)
    FROM BonSortieFille bs JOIN bs.demandeFille df JOIN df.demandeMere dm
    WHERE df.article.codeArticle = :codeArticle
      AND bs.bonSortieMere.statut = 'CONFIRME'
      AND df.typeApprovisionnement = afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement.STOCK
      AND df.statut <> :refuse
      AND dm.statut <> :refuse
      AND dm.typeDemande = afg.achat.afgApprovAchat.model.util.TypeDemande.FOURNITURE
      AND df.id NOT IN :exclues
""")
    double sommeSortiesLignesStock(@Param("codeArticle") String codeArticle,
                                   @Param("refuse") int refuse,
                                   @Param("exclues") Collection<Integer> exclues);

    /**
     * Lignes encore à sortir du stock : lignes non refusées des demandes de matériel validées et
     * non soldées, dont la quantité dépasse les sorties confirmées. Les anciennes demandes (type
     * STANDARD ou sans type) ne sont pas reprises. Triées par demande (la plus ancienne d'abord).
     */
    @Query("""
    SELECT df FROM DemandeFille df JOIN df.demandeMere dm
    WHERE dm.statut = :valide
      AND dm.typeDemande = afg.achat.afgApprovAchat.model.util.TypeDemande.FOURNITURE
      AND (dm.etatLivraison IS NULL
           OR dm.etatLivraison <> afg.achat.afgApprovAchat.model.demande.DemandeMere.EtatLivraison.SOLDEE)
      AND df.statut <> :refuse
      AND df.quantite
          - COALESCE((SELECT SUM(bs.quantiteSortie) FROM BonSortieFille bs
                      WHERE bs.demandeFille = df AND bs.bonSortieMere.statut = 'CONFIRME'), 0)
          > 0.0001
    ORDER BY dm.dateDemande ASC, dm.id ASC, df.id ASC
""")
    List<DemandeFille> lignesASortir(@Param("valide") int valide, @Param("refuse") int refuse);

    /**
     * Calcule la quantité totale réservée en stock pour un article dans une demande spécifique
     * @param demandeId L'ID de la demande mère
     * @param codeArticle Le code article
     * @return La quantité totale réservée en stock (somme des quantiteStock des lignes STOCK)
     */
    @Query("""
    SELECT COALESCE(SUM(df.quantite), 0)
    FROM DemandeFille df
    WHERE df.demandeMere.id = :demandeId
    AND df.article.codeArticle = :codeArticle
    AND df.typeApprovisionnement = :type
    AND df.statut != :refuse
""")
    Double getQuantiteStockReserveePourDemande(
            @Param("demandeId") String demandeId,
            @Param("codeArticle") String codeArticle,
            @Param("type") DemandeFille.TypeApprovisionnement type,
            @Param("refuse") int refuse
    );
}
