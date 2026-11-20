package afg.achat.afgApprovAchat.repository.bonSortie;

import afg.achat.afgApprovAchat.model.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BonSortieMereRepo extends JpaRepository<BonSortieMere, Integer> {
    List<BonSortieMere> findByDemandeMere(DemandeMere demandeMere);
    List<BonSortieMere> findByDemandeMereAndStatut(DemandeMere demandeMere,
                                                   BonSortieMere.StatutBonSortie statut);
    // Compte le nombre de BS pour générer le numéro
    long countByDemandeMere(DemandeMere demandeMere);

    /** Bon verrouillé jusqu'à la fin de la transaction (deux annulations simultanées ne passent pas). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bs FROM BonSortieMere bs WHERE bs.id = :id")
    Optional<BonSortieMere> findByIdPourMiseAJour(@Param("id") int id);

    /**
     * Recherche de la liste des bons de sortie. Chaque filtre texte vide est ignoré ;
     * la période porte sur la date de confirmation (à défaut, la date de création).
     *
     * @param numero    numéro du bon de sortie ou identifiant de la demande
     * @param demandeur nom ou prénom du demandeur
     * @param article   code ou désignation d'un article sorti sur le bon
     */
    @Query(value = """
        SELECT bs FROM BonSortieMere bs
        LEFT JOIN bs.demandeMere dm
        LEFT JOIN dm.demandeur u
        WHERE bs.statut IN :statuts
          AND COALESCE(bs.dateConfirmation, bs.dateCreation) BETWEEN :du AND :au
          AND (:numero = '' OR LOWER(bs.numeroBs) LIKE LOWER(CONCAT('%', :numero, '%'))
                            OR LOWER(dm.id) LIKE LOWER(CONCAT('%', :numero, '%')))
          AND (:demandeur = '' OR LOWER(CONCAT(COALESCE(u.nom, ''), ' ', COALESCE(u.prenom, '')))
                                  LIKE LOWER(CONCAT('%', :demandeur, '%')))
          AND (:article = '' OR EXISTS (
                SELECT 1 FROM BonSortieFille f
                WHERE f.bonSortieMere = bs
                  AND (LOWER(f.article.codeArticle) LIKE LOWER(CONCAT('%', :article, '%'))
                       OR LOWER(f.article.designation) LIKE LOWER(CONCAT('%', :article, '%')))))
        ORDER BY COALESCE(bs.dateConfirmation, bs.dateCreation) DESC, bs.id DESC
        """,
        countQuery = """
        SELECT COUNT(bs) FROM BonSortieMere bs
        LEFT JOIN bs.demandeMere dm
        LEFT JOIN dm.demandeur u
        WHERE bs.statut IN :statuts
          AND COALESCE(bs.dateConfirmation, bs.dateCreation) BETWEEN :du AND :au
          AND (:numero = '' OR LOWER(bs.numeroBs) LIKE LOWER(CONCAT('%', :numero, '%'))
                            OR LOWER(dm.id) LIKE LOWER(CONCAT('%', :numero, '%')))
          AND (:demandeur = '' OR LOWER(CONCAT(COALESCE(u.nom, ''), ' ', COALESCE(u.prenom, '')))
                                  LIKE LOWER(CONCAT('%', :demandeur, '%')))
          AND (:article = '' OR EXISTS (
                SELECT 1 FROM BonSortieFille f
                WHERE f.bonSortieMere = bs
                  AND (LOWER(f.article.codeArticle) LIKE LOWER(CONCAT('%', :article, '%'))
                       OR LOWER(f.article.designation) LIKE LOWER(CONCAT('%', :article, '%')))))
        """)
    Page<BonSortieMere> rechercher(@Param("statuts") List<BonSortieMere.StatutBonSortie> statuts,
                                   @Param("du") LocalDateTime du,
                                   @Param("au") LocalDateTime au,
                                   @Param("numero") String numero,
                                   @Param("demandeur") String demandeur,
                                   @Param("article") String article,
                                   Pageable pageable);
}
