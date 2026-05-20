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

    boolean existsById(String id);
    Optional<DemandeMere> findByCodeProvisoire(String codeProvisoire);

    @Query("""
            SELECT new afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO(
                s.acronyme,
                COUNT(dm)
            )
            FROM DemandeMere dm
            LEFT JOIN dm.demandeur u
            LEFT JOIN u.service s
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
    // Compteurs par statut directement en base
    @Query("""
        SELECT dm.statut, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.id IN :ids
        GROUP BY dm.statut
        """)
    List<Object[]> countByStatut(@Param("ids") List<String> ids);

    // Compteurs par nature
    @Query("""
        SELECT dm.natureDemande, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.id IN :ids
        GROUP BY dm.natureDemande
        """)
    List<Object[]> countByNature(@Param("ids") List<String> ids);

    // Compteurs par priorité
    @Query("""
        SELECT dm.priorite, COUNT(dm)
        FROM DemandeMere dm
        WHERE dm.id IN :ids
        GROUP BY dm.priorite
        """)
    List<Object[]> countByPriorite(@Param("ids") List<String> ids);

    // Évolution mensuelle
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
}