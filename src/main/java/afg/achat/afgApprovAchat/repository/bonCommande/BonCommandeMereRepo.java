package afg.achat.afgApprovAchat.repository.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BonCommandeMereRepo extends JpaRepository<BonCommandeMere, String> {
	List<BonCommandeMere> findByDemandeMereOrderByDateCreationDesc(DemandeMere demandeMere);

	List<BonCommandeMere> findAllByOrderByDateCreationDesc();

	boolean existsByNumero(String numero);

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
		LEFT JOIN bc.fournisseur f
		LEFT JOIN bc.demandeMere dm
		WHERE bc.statut IN :statuts
		  AND (:filtrerEtape = false OR bc.etapeValidation IN :etapes)
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
	                                 Pageable pageable);
}
