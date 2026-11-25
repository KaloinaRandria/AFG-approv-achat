package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeMereRepo
        extends JpaRepository<DemandeMere, String>,
        JpaSpecificationExecutor<DemandeMere> {

    // Statistiques : « dm.statut <> 2 » écarte les brouillons (StatutDemande.BROUILLON),
    // qui ne sont pas encore des demandes soumises.

    boolean existsById(String id);
    Optional<DemandeMere> findByCodeProvisoire(String codeProvisoire);
    List<DemandeMere> findByStatutOrderByDateDemandeAsc(int statut);

    @Query("""
            SELECT new afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO(
                s.acronyme,
                COUNT(dm)
            )
            FROM DemandeMere dm
            LEFT JOIN dm.demandeur u
            LEFT JOIN u.service s
            WHERE dm.statut <> 2
            GROUP BY s.id
            ORDER BY COUNT(dm) DESC limit 5
            """)
    List<ServiceDemandeDTO> countDemandesByService();

    // Toutes les demandes avec filtre date optionnel
    @Query("""
    SELECT dm FROM DemandeMere dm
    LEFT JOIN FETCH dm.demandeur u
    WHERE dm.dateDemande >= :from
      AND dm.dateDemande <= :to
    """)
    List<DemandeMere> findAllWithFilters(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );

    @Query("""
    SELECT dm FROM DemandeMere dm
    LEFT JOIN FETCH dm.demandeur u
    WHERE u.id IN :ids
      AND dm.dateDemande >= :from
      AND dm.dateDemande <= :to
    """)
    List<DemandeMere> findByDemandeurIdsWithFilters(
            @Param("ids")  List<Integer> ids,
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );

    /**
     * Délai de bout en bout des demandes entièrement livrées (soldées) dont la dernière sortie
     * confirmée tombe dans la période : [nombre, jours moyens, jours médians, livrées au plus tard
     * à la date souhaitée, demandes ayant une date souhaitée].
     */
    @Query(nativeQuery = true, value = """
        WITH fin AS (
            SELECT bs.id_demande_mere, MAX(COALESCE(bs.date_confirmation, bs.date_creation)) AS date_fin
            FROM bon_sortie_mere bs
            WHERE bs.statut = 'CONFIRME'
            GROUP BY bs.id_demande_mere
        ), d AS (
            SELECT EXTRACT(EPOCH FROM (f.date_fin - dm.date_demande)) / 86400.0 AS jours,
                   CASE WHEN dm.date_sortie IS NULL THEN NULL
                        WHEN CAST(f.date_fin AS date) <= CAST(dm.date_sortie AS date) THEN 1 ELSE 0 END AS a_temps
            FROM demande_mere dm
            JOIN fin f ON f.id_demande_mere = dm.id_demande_mere
            WHERE dm.etat_livraison = 'SOLDEE'
              AND f.date_fin BETWEEN :du AND :au
        )
        SELECT COUNT(*), AVG(jours), PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY jours),
               COALESCE(SUM(a_temps), 0), COUNT(a_temps)
        FROM d
        """)
    List<Object[]> delaiLivraison(@Param("du") LocalDateTime du, @Param("au") LocalDateTime au);

    /**
     * Dépenses par service du demandeur sur la période (hors brouillons et refus) : nombre de
     * demandes, montant en ariary des demandes validées, puis de celles encore dans le circuit.
     */
    @Query("""
        SELECT new afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO(
            s.acronyme, COUNT(dm),
            SUM(CASE WHEN dm.statut = 15 THEN COALESCE(dm.totalEstimeAriary, dm.totalPrixAriary, 0.0) ELSE 0.0 END),
            SUM(CASE WHEN dm.statut <> 15 THEN COALESCE(dm.totalEstimeAriary, dm.totalPrixAriary, 0.0) ELSE 0.0 END))
        FROM DemandeMere dm LEFT JOIN dm.demandeur u LEFT JOIN u.service s
        WHERE dm.dateDemande >= :from AND dm.dateDemande <= :to
          AND dm.statut <> 2 AND dm.statut <> -1
        GROUP BY s.acronyme
        """)
    List<ServiceDemandeDTO> depensesParServiceAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT new afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO(
            s.acronyme, COUNT(dm),
            SUM(CASE WHEN dm.statut = 15 THEN COALESCE(dm.totalEstimeAriary, dm.totalPrixAriary, 0.0) ELSE 0.0 END),
            SUM(CASE WHEN dm.statut <> 15 THEN COALESCE(dm.totalEstimeAriary, dm.totalPrixAriary, 0.0) ELSE 0.0 END))
        FROM DemandeMere dm LEFT JOIN dm.demandeur u LEFT JOIN u.service s
        WHERE u.id IN :ids
          AND dm.dateDemande >= :from AND dm.dateDemande <= :to
          AND dm.statut <> 2 AND dm.statut <> -1
        GROUP BY s.acronyme
        """)
    List<ServiceDemandeDTO> depensesParServiceByDemandeurIds(@Param("ids") List<Integer> ids,
                                                             @Param("from") LocalDateTime from,
                                                             @Param("to") LocalDateTime to);

    // Compteurs par statut (All vs Filtered)
    @Query("""
        SELECT dm.statut, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.dateDemande >= :from AND dm.dateDemande <= :to
          AND dm.statut <> 2
        GROUP BY dm.statut
        """)
    List<Object[]> countByStatutAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT dm.statut, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.demandeur.id IN :ids
          AND dm.dateDemande >= :from AND dm.dateDemande <= :to
            AND dm.statut <> 2
        GROUP BY dm.statut
        """)
    List<Object[]> countByStatutByDemandeurIds(@Param("ids") List<Integer> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Compteurs par nature
    @Query("""
        SELECT dm.natureDemande, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.dateDemande >= :from AND dm.dateDemande <= :to
          AND dm.statut <> 2
        GROUP BY dm.natureDemande
        """)
    List<Object[]> countByNatureAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT dm.natureDemande, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.demandeur.id IN :ids
          AND dm.dateDemande >= :from AND dm.dateDemande <= :to
            AND dm.statut <> 2
        GROUP BY dm.natureDemande
        """)
    List<Object[]> countByNatureByDemandeurIds(@Param("ids") List<Integer> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Compteurs par priorité
    @Query("""
        SELECT dm.priorite, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.dateDemande >= :from AND dm.dateDemande <= :to
          AND dm.statut <> 2
        GROUP BY dm.priorite
        """)
    List<Object[]> countByPrioriteAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT dm.priorite, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.demandeur.id IN :ids
          AND dm.dateDemande >= :from AND dm.dateDemande <= :to
            AND dm.statut <> 2
        GROUP BY dm.priorite
        """)
    List<Object[]> countByPrioriteByDemandeurIds(@Param("ids") List<Integer> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Évolution mensuelle
    @Query(value = """
    SELECT EXTRACT(YEAR  FROM dm.date_demande) AS annee,
           EXTRACT(MONTH FROM dm.date_demande) AS mois,
           dm.statut,
           COUNT(dm.id_demande_mere)
    FROM demande_mere dm
    WHERE dm.date_demande >= :from AND dm.date_demande <= :to
      AND dm.statut <> 2
    GROUP BY EXTRACT(YEAR  FROM dm.date_demande),
             EXTRACT(MONTH FROM dm.date_demande),
             dm.statut
    ORDER BY 1, 2
    """, nativeQuery = true)
    List<Object[]> countByMoisAndStatutAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT EXTRACT(YEAR  FROM dm.date_demande) AS annee,
           EXTRACT(MONTH FROM dm.date_demande) AS mois,
           dm.statut,
           COUNT(dm.id_demande_mere)
    FROM demande_mere dm
    WHERE dm.id_demandeur IN :ids
      AND dm.date_demande >= :from AND dm.date_demande <= :to
        AND dm.statut <> 2
    GROUP BY EXTRACT(YEAR  FROM dm.date_demande),
             EXTRACT(MONTH FROM dm.date_demande),
             dm.statut
    ORDER BY 1, 2
    """, nativeQuery = true)
    List<Object[]> countByMoisAndStatutByDemandeurIds(@Param("ids") List<Integer> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Délais moyens d'approbation (SLA)
    @Query(value = """
    SELECT CAST(dm.priorite AS VARCHAR) AS priorite,
           AVG(EXTRACT(EPOCH FROM (COALESCE(v.max_date, dm.date_demande) - dm.date_demande)) / 86400.0) AS avg_days
    FROM demande_mere dm
    LEFT JOIN (
        SELECT id_demande_mere, MAX(date_action) AS max_date
        FROM validation_demande
        WHERE decision = 'APPROUVE'
        GROUP BY id_demande_mere
    ) v ON dm.id_demande_mere = v.id_demande_mere
    WHERE dm.statut = 15
      AND dm.date_demande >= :from AND dm.date_demande <= :to
        AND dm.statut <> 2
    GROUP BY dm.priorite
    """, nativeQuery = true)
    List<Object[]> findAverageApprovalDelayByPriorityAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT CAST(dm.priorite AS VARCHAR) AS priorite,
           AVG(EXTRACT(EPOCH FROM (COALESCE(v.max_date, dm.date_demande) - dm.date_demande)) / 86400.0) AS avg_days
    FROM demande_mere dm
    LEFT JOIN (
        SELECT id_demande_mere, MAX(date_action) AS max_date
        FROM validation_demande
        WHERE decision = 'APPROUVE'
        GROUP BY id_demande_mere
    ) v ON dm.id_demande_mere = v.id_demande_mere
    WHERE dm.statut = 15
      AND dm.id_demandeur IN :ids
      AND dm.date_demande >= :from AND dm.date_demande <= :to
        AND dm.statut <> 2
    GROUP BY dm.priorite
    """, nativeQuery = true)
    List<Object[]> findAverageApprovalDelayByPriorityByDemandeurIds(@Param("ids") List<Integer> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Délais moyens d'approbation SLA personnel par validateur
    @Query(value = """
    SELECT CAST(dm.priorite AS VARCHAR) AS priorite,
           AVG(EXTRACT(EPOCH FROM (v.date_action - COALESCE(prev.prev_date, dm.date_demande))) / 86400.0) AS avg_days
    FROM validation_demande v
    JOIN demande_mere dm ON v.id_demande_mere = dm.id_demande_mere
    LEFT JOIN (
        SELECT id_validation_demande,
               LAG(date_action) OVER (PARTITION BY id_demande_mere ORDER BY date_action) AS prev_date
        FROM validation_demande
    ) prev ON v.id_validation_demande = prev.id_validation_demande
    WHERE v.id_validateur = :validateurId
      AND dm.date_demande >= :from AND dm.date_demande <= :to
        AND dm.statut <> 2
    GROUP BY dm.priorite
    """, nativeQuery = true)
    List<Object[]> findAverageApprovalDelayByPriorityAndValidateurId(
            @Param("validateurId") Integer validateurId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // Deprecated old methods kept for backwards compatibility if referenced elsewhere
    @Query("""
        SELECT dm.statut, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.id IN :ids
        GROUP BY dm.statut
        """)
    List<Object[]> countByStatut(@Param("ids") List<String> ids);

    @Query("""
        SELECT dm.natureDemande, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.id IN :ids
        GROUP BY dm.natureDemande
        """)
    List<Object[]> countByNature(@Param("ids") List<String> ids);

    @Query("""
        SELECT dm.priorite, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.id IN :ids
        GROUP BY dm.priorite
        """)
    List<Object[]> countByPriorite(@Param("ids") List<String> ids);

    @Query(value = """
    SELECT EXTRACT(YEAR  FROM dm.date_demande) AS annee,
           EXTRACT(MONTH FROM dm.date_demande) AS mois,
           dm.statut,
           COUNT(dm.id_demande_mere)
    FROM demande_mere dm
    WHERE dm.id_demande_mere IN :ids
      AND dm.date_demande IS NOT NULL
    GROUP BY EXTRACT(YEAR  FROM dm.date_demande),
             EXTRACT(MONTH FROM dm.date_demande),
             dm.statut
    ORDER BY 1, 2
    """, nativeQuery = true)
    List<Object[]> countByMoisAndStatut(@Param("ids") List<String> ids);

    // ── Tableau de bord : éléments à traiter ────────────────────────────────

    /** Par statut : [statut, nombre, date de la plus ancienne demande]. */
    @Query("""
        SELECT d.statut, COUNT(d), MIN(d.dateDemande) FROM DemandeMere d
        WHERE d.statut IN :statuts
        GROUP BY d.statut
        """)
    List<Object[]> compterParStatut(@Param("statuts") java.util.Collection<Integer> statuts);

    /** Demandes d'un statut émises par certains demandeurs : [nombre, plus ancienne date]. */
    @Query("""
        SELECT COUNT(d), MIN(d.dateDemande) FROM DemandeMere d
        WHERE d.statut = :statut AND d.demandeur.id IN :demandeurIds
        """)
    List<Object[]> compterStatutPourDemandeurs(@Param("statut") int statut,
                                               @Param("demandeurIds") java.util.Collection<Integer> demandeurIds);

    /** Paiements directs validés à un stade donné : [nombre, plus ancienne date de référence]. */
    @Query("""
        SELECT COUNT(d), MIN(COALESCE(d.dateTransmissionFinance, d.dateDemande)) FROM DemandeMere d
        WHERE d.statut = :statut AND d.statutTransmissionFinance = :transmission
        """)
    List<Object[]> compterPaiementsDirects(@Param("statut") int statut,
                                           @Param("transmission") DemandeMere.StatutTransmissionFinance transmission);

    /** Dernières demandes d'un demandeur (bloc « Mes demandes »). */
    List<DemandeMere> findTop5ByDemandeurIdOrderByDateDemandeDesc(int demandeurId);
}
