package afg.achat.afgApprovAchat.repository.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BonCommandeMereRepo extends JpaRepository<BonCommandeMere, String> {
	List<BonCommandeMere> findByDemandeMereOrderByDateCreationDesc(DemandeMere demandeMere);

	List<BonCommandeMere> findAllByOrderByDateCreationDesc();

	boolean existsByNumero(String numero);

	/** Charge le bon en le verrouillant jusqu'à la fin de la transaction : deux réceptions du même bon passent l'une après l'autre. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT bc FROM BonCommandeMere bc WHERE bc.id = :id")
	Optional<BonCommandeMere> verrouiller(@Param("id") String id);

	/**
	 * Recherche multicritère de la liste des bons de commande. Chaque filtre texte vide est
	 * ignoré ; la période porte sur la date de création.
	 *
	 * @param numero       numéro ou identifiant du bon
	 * @param demande      identifiant d'une demande : demande d'origine (ancien circuit) ou
	 *                     demande dont une ligne figure sur le bon
	 * @param article      code ou désignation d'un article, ou désignation libre d'une ligne
	 * @param filtrerEtape {@code true} : seulement les bons en attente à l'une des {@code etapes}
	 */
	@Query(value = """
		SELECT bc FROM BonCommandeMere bc
		LEFT JOIN FETCH bc.fournisseur f
		LEFT JOIN FETCH bc.demandeMere dm
		WHERE bc.statut IN :statuts
		  AND (:filtrerEtape = false OR bc.etapeValidation IN :etapes)
		  AND (:aRecevoir = false OR bc.statutReception IS NULL OR bc.statutReception IN :receptionsOuvertes)
		  AND bc.dateCreation BETWEEN :du AND :au
		  AND (:numero = '' OR LOWER(COALESCE(bc.numero, '')) LIKE LOWER(CONCAT('%', :numero, '%'))
		                    OR LOWER(bc.id) LIKE LOWER(CONCAT('%', :numero, '%')))
		  AND (:fournisseur = '' OR LOWER(COALESCE(f.nom, '')) LIKE LOWER(CONCAT('%', :fournisseur, '%')))
		  AND (:objet = '' OR LOWER(COALESCE(bc.description, '')) LIKE LOWER(CONCAT('%', :objet, '%')))
		  AND (:demande = '' OR LOWER(COALESCE(dm.id, '')) LIKE LOWER(CONCAT('%', :demande, '%'))
		       OR EXISTS (SELECT 1 FROM BonCommandeFille l
		                  JOIN l.demandeFille ldf JOIN ldf.demandeMere ldm
		                  WHERE l.bonCommandeMere = bc
		                    AND LOWER(ldm.id) LIKE LOWER(CONCAT('%', :demande, '%'))))
		  AND (:article = '' OR EXISTS (SELECT 1 FROM BonCommandeFille l
		                  LEFT JOIN l.article a
		                  WHERE l.bonCommandeMere = bc
		                    AND (LOWER(COALESCE(a.codeArticle, '')) LIKE LOWER(CONCAT('%', :article, '%'))
		                         OR LOWER(COALESCE(a.designation, '')) LIKE LOWER(CONCAT('%', :article, '%'))
		                         OR LOWER(COALESCE(l.designation, '')) LIKE LOWER(CONCAT('%', :article, '%')))))
		ORDER BY bc.dateCreation DESC, bc.id DESC
		""",
		countQuery = """
		SELECT COUNT(bc) FROM BonCommandeMere bc
		LEFT JOIN bc.fournisseur f
		LEFT JOIN bc.demandeMere dm
		WHERE bc.statut IN :statuts
		  AND (:filtrerEtape = false OR bc.etapeValidation IN :etapes)
		  AND (:aRecevoir = false OR bc.statutReception IS NULL OR bc.statutReception IN :receptionsOuvertes)
		  AND bc.dateCreation BETWEEN :du AND :au
		  AND (:numero = '' OR LOWER(COALESCE(bc.numero, '')) LIKE LOWER(CONCAT('%', :numero, '%'))
		                    OR LOWER(bc.id) LIKE LOWER(CONCAT('%', :numero, '%')))
		  AND (:fournisseur = '' OR LOWER(COALESCE(f.nom, '')) LIKE LOWER(CONCAT('%', :fournisseur, '%')))
		  AND (:objet = '' OR LOWER(COALESCE(bc.description, '')) LIKE LOWER(CONCAT('%', :objet, '%')))
		  AND (:demande = '' OR LOWER(COALESCE(dm.id, '')) LIKE LOWER(CONCAT('%', :demande, '%'))
		       OR EXISTS (SELECT 1 FROM BonCommandeFille l
		                  JOIN l.demandeFille ldf JOIN ldf.demandeMere ldm
		                  WHERE l.bonCommandeMere = bc
		                    AND LOWER(ldm.id) LIKE LOWER(CONCAT('%', :demande, '%'))))
		  AND (:article = '' OR EXISTS (SELECT 1 FROM BonCommandeFille l
		                  LEFT JOIN l.article a
		                  WHERE l.bonCommandeMere = bc
		                    AND (LOWER(COALESCE(a.codeArticle, '')) LIKE LOWER(CONCAT('%', :article, '%'))
		                         OR LOWER(COALESCE(a.designation, '')) LIKE LOWER(CONCAT('%', :article, '%'))
		                         OR LOWER(COALESCE(l.designation, '')) LIKE LOWER(CONCAT('%', :article, '%')))))
		""")
	Page<BonCommandeMere> rechercher(@Param("statuts") List<BonCommandeMere.StatutBonCommande> statuts,
	                                 @Param("filtrerEtape") boolean filtrerEtape,
	                                 @Param("etapes") List<BonCommandeMere.EtapeValidation> etapes,
	                                 @Param("du") LocalDateTime du,
	                                 @Param("au") LocalDateTime au,
	                                 @Param("numero") String numero,
	                                 @Param("fournisseur") String fournisseur,
	                                 @Param("objet") String objet,
	                                 @Param("demande") String demande,
	                                 @Param("article") String article,
	                                 @Param("aRecevoir") boolean aRecevoir,
	                                 @Param("receptionsOuvertes") List<BonCommandeMere.StatutReception> receptionsOuvertes,
	                                 Pageable pageable);

	// ── Tableau de bord ─────────────────────────────────────────────────────

	/** Bons en attente de validation : [étape, nombre, plus ancienne date de soumission]. */
	@Query("""
		SELECT bc.etapeValidation, COUNT(bc), MIN(COALESCE(bc.dateSoumission, bc.dateCreation))
		FROM BonCommandeMere bc
		WHERE bc.statut = :statut
		GROUP BY bc.etapeValidation
		""")
	List<Object[]> compterEnAttenteParEtape(@Param("statut") BonCommandeMere.StatutBonCommande statut);

	/** Bons d'un statut : [nombre, dont rejetés (motif présent), plus ancienne date de création]. */
	@Query("""
		SELECT COUNT(bc), SUM(CASE WHEN bc.motifRejet IS NOT NULL THEN 1 ELSE 0 END), MIN(bc.dateCreation)
		FROM BonCommandeMere bc
		WHERE bc.statut = :statut
		""")
	List<Object[]> compterParStatutAvecRejets(@Param("statut") BonCommandeMere.StatutBonCommande statut);

	/** Bons validés encore à réceptionner : [nombre, dont livraison prévue dépassée, plus ancienne date prévue]. */
	@Query("""
		SELECT COUNT(bc), SUM(CASE WHEN bc.dateLivraisonPrevue < :maintenant THEN 1 ELSE 0 END),
		       MIN(bc.dateLivraisonPrevue)
		FROM BonCommandeMere bc
		WHERE bc.statut = :statut
		  AND (bc.statutReception IS NULL OR bc.statutReception IN :receptionsOuvertes)
		""")
	List<Object[]> compterAReceptionner(@Param("statut") BonCommandeMere.StatutBonCommande statut,
	                                    @Param("receptionsOuvertes") List<BonCommandeMere.StatutReception> receptionsOuvertes,
	                                    @Param("maintenant") LocalDateTime maintenant);

	/**
	 * Bons validés sur la période (date de validation S.G., à défaut de création), par devise :
	 * [code devise (null = bon antérieur, en ariary), nombre, montant TTC total]. Les montants de
	 * devises différentes ne s'additionnent pas.
	 */
	@Query("""
		SELECT d.acronyme, COUNT(bc), COALESCE(SUM(bc.montantTTC), 0)
		FROM BonCommandeMere bc LEFT JOIN bc.devise d
		WHERE bc.statut = :statut
		  AND COALESCE(bc.dateValidationSg, bc.dateCreation) BETWEEN :du AND :au
		GROUP BY d.acronyme
		""")
	List<Object[]> totauxValides(@Param("statut") BonCommandeMere.StatutBonCommande statut,
	                             @Param("du") LocalDateTime du, @Param("au") LocalDateTime au);

	/**
	 * Fournisseurs des bons validés sur la période, par devise : [nom, code devise (null = ariary),
	 * nombre de bons, montant TTC]. Classés par contre-valeur en ariary (cours copié sur chaque bon),
	 * seulement pour l'ordre : les montants restent affichés dans leur devise.
	 */
	@Query("""
		SELECT f.nom, d.acronyme, COUNT(bc), SUM(bc.montantTTC)
		FROM BonCommandeMere bc JOIN bc.fournisseur f LEFT JOIN bc.devise d
		WHERE bc.statut = :statut
		  AND COALESCE(bc.dateValidationSg, bc.dateCreation) BETWEEN :du AND :au
		GROUP BY f.nom, d.acronyme
		ORDER BY SUM(bc.montantTTC * COALESCE(bc.tauxChange, 1)) DESC
		""")
	List<Object[]> topFournisseurs(@Param("statut") BonCommandeMere.StatutBonCommande statut,
	                               @Param("du") LocalDateTime du, @Param("au") LocalDateTime au,
	                               Pageable limite);

	/** Bons validés sur la période par avancement de réception : [statut de réception (null = non reçu), nombre]. */
	@Query("""
		SELECT bc.statutReception, COUNT(bc)
		FROM BonCommandeMere bc
		WHERE bc.statut = :statut
		  AND COALESCE(bc.dateValidationSg, bc.dateCreation) BETWEEN :du AND :au
		GROUP BY bc.statutReception
		""")
	List<Object[]> receptionsParStatut(@Param("statut") BonCommandeMere.StatutBonCommande statut,
	                                   @Param("du") LocalDateTime du, @Param("au") LocalDateTime au);
}
