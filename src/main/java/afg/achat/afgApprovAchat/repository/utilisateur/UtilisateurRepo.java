package afg.achat.afgApprovAchat.repository.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UtilisateurRepo extends JpaRepository<Utilisateur,Integer> {
    List<Utilisateur> findBySuperieurHierarchique_Id(int superieurId);

    @Query("select u.id from Utilisateur u where u.superieurHierarchique.id = :superieurId")
    List<Integer> findIdsBySuperieur(@Param("superieurId") int superieurId);

    @Query("SELECT u FROM Utilisateur u WHERE u.mail = :mail")
    Utilisateur findByMail(String mail);

    @Query("SELECT u FROM Utilisateur u JOIN u.roles r WHERE r.role = :roleLibelle")
    List<Utilisateur> findByRoleLibelle(@Param("roleLibelle") String roleLibelle);


    @Query("SELECT u FROM Utilisateur u JOIN u.validateurs v WHERE v.id = :validateurId")
    List<Utilisateur> findUtilisateursAValiderByValidateurId(@Param("validateurId") Integer validateurId);

    @Query("SELECT u.id FROM Utilisateur u JOIN u.validateurs v WHERE v.id = :validateurId")
    List<Integer> findIdsUtilisateursAValiderByValidateurId(@Param("validateurId") Integer validateurId);
}
