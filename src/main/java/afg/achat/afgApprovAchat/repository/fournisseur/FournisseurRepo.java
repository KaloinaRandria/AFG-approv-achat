package afg.achat.afgApprovAchat.repository.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FournisseurRepo extends JpaRepository<Fournisseur, Integer>
{
    Boolean existsFournisseurByNomIgnoreCase(String nom);

    /**
     * Recherche multicritère de la liste des fournisseurs, triée par nom.
     * Chaque filtre vide est ignoré.
     *
     * @param nom nom ou acronyme
     */
    @Query(value = """
        SELECT f FROM Fournisseur f
        WHERE (:nom = '' OR LOWER(COALESCE(f.nom, '')) LIKE LOWER(CONCAT('%', :nom, '%'))
                         OR LOWER(COALESCE(f.acronyme, '')) LIKE LOWER(CONCAT('%', :nom, '%')))
          AND (:mail = '' OR LOWER(COALESCE(f.mail, '')) LIKE LOWER(CONCAT('%', :mail, '%')))
          AND (:contact = '' OR LOWER(COALESCE(f.contact, '')) LIKE LOWER(CONCAT('%', :contact, '%')))
        ORDER BY LOWER(f.nom) ASC, f.id ASC
        """,
        countQuery = """
        SELECT COUNT(f) FROM Fournisseur f
        WHERE (:nom = '' OR LOWER(COALESCE(f.nom, '')) LIKE LOWER(CONCAT('%', :nom, '%'))
                         OR LOWER(COALESCE(f.acronyme, '')) LIKE LOWER(CONCAT('%', :nom, '%')))
          AND (:mail = '' OR LOWER(COALESCE(f.mail, '')) LIKE LOWER(CONCAT('%', :mail, '%')))
          AND (:contact = '' OR LOWER(COALESCE(f.contact, '')) LIKE LOWER(CONCAT('%', :contact, '%')))
        """)
    Page<Fournisseur> rechercher(@Param("nom") String nom,
                                 @Param("mail") String mail,
                                 @Param("contact") String contact,
                                 Pageable pageable);
}
