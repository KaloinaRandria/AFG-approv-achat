package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.ValidationDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationDemandeRepo extends JpaRepository<ValidationDemande, Integer> {
    List<ValidationDemande> findByDemandeMereOrderByDateActionAsc(DemandeMere demandeMere);

    /** Refus reçus par un demandeur depuis une date, du plus récent au plus ancien. */
    @Query("""
        SELECT v FROM ValidationDemande v JOIN FETCH v.demandeMere d
        WHERE v.decision = :refus AND d.demandeur.id = :demandeurId AND v.dateAction >= :depuis
        ORDER BY v.dateAction DESC
        """)
    List<ValidationDemande> refusRecus(@org.springframework.data.repository.query.Param("demandeurId") int demandeurId,
                                       @org.springframework.data.repository.query.Param("depuis") java.time.LocalDateTime depuis,
                                       @org.springframework.data.repository.query.Param("refus") ValidationDemande.DecisionValidation refus);

    /**
     * Dernière action à chaque étape, pour les demandes émises sur la période :
     * [id demande, date de la demande, statut actuel, étape, date de la dernière action à cette étape].
     * La dernière action d'une étape quittée marque la sortie de cette étape.
     */
    @Query("""
        SELECT d.id, d.dateDemande, d.statut, v.etape, MAX(v.dateAction)
        FROM ValidationDemande v JOIN v.demandeMere d
        WHERE d.dateDemande BETWEEN :du AND :au AND v.etape IS NOT NULL
        GROUP BY d.id, d.dateDemande, d.statut, v.etape
        """)
    List<Object[]> dernieresActionsParEtape(@org.springframework.data.repository.query.Param("du") java.time.LocalDateTime du,
                                           @org.springframework.data.repository.query.Param("au") java.time.LocalDateTime au);
}