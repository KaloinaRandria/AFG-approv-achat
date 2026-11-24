package afg.achat.afgApprovAchat.repository.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UtilisateurRepo extends JpaRepository<Utilisateur, Integer>, JpaSpecificationExecutor<Utilisateur> {
    List<Utilisateur> findBySuperieurHierarchique_Id(int superieurId);

    @Query("select u.id from Utilisateur u where u.superieurHierarchique.id = :superieurId")
    List<Integer> findIdsBySuperieur(@Param("superieurId") int superieurId);

    @Query("SELECT u FROM Utilisateur u WHERE u.mail = :mail")
    Utilisateur findByMail(String mail);

    /** Utilisateurs actifs ayant ce rôle (destinataires de mails et de tâches). */
    @Query("SELECT u FROM Utilisateur u JOIN u.roles r WHERE r.role = :roleLibelle AND u.actif = true")
    List<Utilisateur> findByRoleLibelle(@Param("roleLibelle") String roleLibelle);


    @Query("SELECT u FROM Utilisateur u JOIN u.validateurs v WHERE v.id = :validateurId")
    List<Utilisateur> findUtilisateursAValiderByValidateurId(@Param("validateurId") Integer validateurId);

    @Query("SELECT u.id FROM Utilisateur u JOIN u.validateurs v WHERE v.id = :validateurId")
    List<Integer> findIdsUtilisateursAValiderByValidateurId(@Param("validateurId") Integer validateurId);

    /** Comptes actifs, triés pour les listes de choix (supérieur, validateurs). */
    List<Utilisateur> findByActifTrueOrderByNomAscPrenomAsc();

    /** Nombre d utilisateurs par poste : [id poste, nombre]. */
    @Query("SELECT u.poste.id, COUNT(u) FROM Utilisateur u WHERE u.poste IS NOT NULL GROUP BY u.poste.id")
    List<Object[]> compterParPoste();

    /** Nombre d utilisateurs par direction (service) : [id service, nombre]. */
    @Query("SELECT u.service.id, COUNT(u) FROM Utilisateur u WHERE u.service IS NOT NULL GROUP BY u.service.id")
    List<Object[]> compterParService();

    long countByPoste_Id(int idPoste);

    long countByService_Id(int idService);

    /** Utilisateurs dont la direction ne correspond pas à celle de leur poste (poste rattaché). */
    @Query("""
        SELECT u FROM Utilisateur u JOIN u.poste p
        WHERE p.direction IS NOT NULL
          AND (u.service IS NULL OR u.service <> p.direction)
        ORDER BY u.nom, u.prenom
        """)
    List<Utilisateur> findIncoherentsPosteDirection();
}
