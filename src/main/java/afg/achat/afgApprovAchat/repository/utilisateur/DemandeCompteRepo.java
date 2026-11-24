package afg.achat.afgApprovAchat.repository.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.DemandeCompte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandeCompteRepo extends JpaRepository<DemandeCompte, Integer> {

    List<DemandeCompte> findByStatutOrderByDateDemandeAsc(DemandeCompte.Statut statut);

    /** Dernières demandes traitées (historique). */
    List<DemandeCompte> findTop30ByStatutNotOrderByDateDecisionDesc(DemandeCompte.Statut statut);

    boolean existsByCibleIdAndStatut(int cibleId, DemandeCompte.Statut statut);

    long countByStatut(DemandeCompte.Statut statut);

    /** Comptes ayant une demande en attente (badge dans la liste des utilisateurs). */
    @Query("SELECT d.cible.id FROM DemandeCompte d WHERE d.statut = :statut AND d.cible IS NOT NULL")
    List<Integer> idsCiblesParStatut(@Param("statut") DemandeCompte.Statut statut);

    /** Demandes en attente que l'utilisateur peut valider (pas les siennes) : [nombre, plus ancienne date]. */
    @Query("""
        SELECT COUNT(d), MIN(d.dateDemande) FROM DemandeCompte d
        WHERE d.statut = :statut AND d.demandeur.id <> :utilisateurId
        """)
    List<Object[]> compterAValiderPar(@Param("statut") DemandeCompte.Statut statut,
                                      @Param("utilisateurId") int utilisateurId);
}
