package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeMereRepo extends JpaRepository<DemandeMere, String> {

    @Query("""
        select dm from DemandeMere dm
        join dm.demandeur dmd
        where dm.dateDemande between :dateFrom and :dateTo
          and (
                :q = ''
                or lower(dm.id) like lower(concat('%', :q, '%'))
                or lower(coalesce(dm.natureDemande, '')) like lower(concat('%', :q, '%'))
              
                or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(dmd.nom, '')) like lower(concat('%', :q, '%'))
          )
    """)
    Page<DemandeMere> search(
            @Param("q") String q,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    @Query("""
        select dm from DemandeMere dm
        join dm.demandeur dmd
        where dm.dateDemande between :dateFrom and :dateTo
          and dmd.id = :demandeurId
          and (
                :q = ''
                or lower(dm.id) like lower(concat('%', :q, '%'))
                or lower(coalesce(dm.natureDemande, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(dmd.nom, '')) like lower(concat('%', :q, '%'))
          )
    """)
    Page<DemandeMere> searchByDemandeur(
            @Param("q") String q,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("demandeurId") String demandeurId,
            Pageable pageable
    );

    @Query("""
        select dm from DemandeMere dm
        join dm.demandeur dmd
        where dm.dateDemande between :dateFrom and :dateTo
          and dmd.id in :demandeurIds
          and (
                :q = ''
                or lower(dm.id) like lower(concat('%', :q, '%'))
                or lower(coalesce(dm.natureDemande, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(dmd.nom, '')) like lower(concat('%', :q, '%'))
          )
    """)
    Page<DemandeMere> searchByDemandeurIds(
            @Param("q") String q,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("demandeurIds") List<Integer> demandeurIds,
            Pageable pageable
    );

    @Query("""
    select dm from DemandeMere dm
    join dm.demandeur dmd
    where dm.dateDemande between :dateFrom and :dateTo

      and (:num = '' or lower(dm.id) like lower(concat('%', :num, '%')))

      and (
            :demandeur = ''
            or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :demandeur, '%'))
            or lower(coalesce(dmd.nom, '')) like lower(concat('%', :demandeur, '%'))
      )

      and (:type = '' or lower(coalesce(dm.natureDemande, '')) = lower(:type))

      and (:statut is null or dm.statut = :statut)
      
      and (:priorite = '' or lower(coalesce(dm.priorite, '')) = lower(:priorite))
      and (:motif = '' or lower(coalesce(dm.motifEvoque, '')) like lower(concat('%', :motif, '%')))
""")
    Page<DemandeMere> searchMulti(
            @Param("num") String num,
            @Param("demandeur") String demandeur,
            @Param("type") String type,
            @Param("statut") Integer statut,
            @Param("priorite") String priorite,
            @Param("motif") String motif,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );



    @Query("""
    select dm from DemandeMere dm
    join dm.demandeur dmd
    where dm.dateDemande between :dateFrom and :dateTo
      and dmd.id in :demandeurIds

      and (:num = '' or lower(dm.id) like lower(concat('%', :num, '%')))

      and (
            :demandeur = ''
            or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :demandeur, '%'))
            or lower(coalesce(dmd.nom, '')) like lower(concat('%', :demandeur, '%'))
      )

      and (:type = '' or lower(coalesce(dm.natureDemande, '')) = lower(:type))

      and (:statut is null or dm.statut = :statut)
      and (:priorite = '' or lower(coalesce(dm.priorite, '')) = lower(:priorite))
      and (:motif = '' or lower(coalesce(dm.motifEvoque, '')) like lower(concat('%', :motif, '%')))
""")
    Page<DemandeMere> searchMultiByDemandeurIds(
            @Param("num") String num,
            @Param("demandeur") String demandeur,
            @Param("type") String type,
            @Param("statut") Integer statut,
            @Param("priorite") String priorite,
            @Param("motif") String motif,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("demandeurIds") List<Integer> demandeurIds,
            Pageable pageable
    );

    @Query("""
    select dm from DemandeMere dm
    join dm.demandeur dmd
    where dm.dateDemande between :dateFrom and :dateTo
      and (:num = '' or lower(dm.id) like lower(concat('%', :num, '%')))
      and (
            :demandeur = ''
            or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :demandeur, '%'))
            or lower(coalesce(dmd.nom, '')) like lower(concat('%', :demandeur, '%'))
      )
      and (:type = '' or lower(coalesce(dm.natureDemande, '')) = lower(:type))
      and (
            :statuts is null
            or dm.statut in :statuts
      )
""")
    Page<DemandeMere> searchMultiWithStatuts(
            @Param("num") String num,
            @Param("demandeur") String demandeur,
            @Param("type") String type,
            @Param("statuts") List<Integer> statuts,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    boolean existsById(String id);

    Optional<DemandeMere> findByCodeProvisoire(String codeProvisoire);
    @Query("""
    select dm from DemandeMere dm
    join dm.demandeur dmd
    where dm.dateDemande between :dateFrom and :dateTo
      and dmd.id in :demandeurIds
      and (:num = '' or lower(dm.id) like lower(concat('%', :num, '%')))
      and (
            :demandeur = ''
            or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :demandeur, '%'))
            or lower(coalesce(dmd.nom, '')) like lower(concat('%', :demandeur, '%'))
      )
      and (:type = '' or lower(coalesce(dm.natureDemande, '')) = lower(:type))
      and (:priorite = '' or lower(coalesce(dm.priorite, '')) = lower(:priorite))
      and (:statuts is null or dm.statut in :statuts)
""")
    Page<DemandeMere> searchMultiByDemandeurIdsAndStatuts(
            @Param("num") String num,
            @Param("demandeur") String demandeur,
            @Param("type") String type,
            @Param("priorite") String priorite,
            @Param("statuts") List<Integer> statuts,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("demandeurIds") List<Integer> demandeurIds,
            Pageable pageable
    );

    @Query("""
    select dm from DemandeMere dm
    join dm.demandeur dmd
    where dm.dateDemande between :dateFrom and :dateTo

      and (:num = '' or lower(dm.id) like lower(concat('%', :num, '%')))

      and (
            :demandeur = ''
            or lower(coalesce(dmd.prenom, '')) like lower(concat('%', :demandeur, '%'))
            or lower(coalesce(dmd.nom,    '')) like lower(concat('%', :demandeur, '%'))
      )

      and (:type     = '' or lower(coalesce(dm.natureDemande, '')) = lower(:type))
      and (:priorite = '' or lower(coalesce(dm.priorite,      '')) = lower(:priorite))

      and (
            (:roleStatuts is null or dm.statut in :roleStatuts)
            or
            (dmd.id in :myVisibleIds and (:myStatut is null or dm.statut = :myStatut))
      )
      and (:motif = '' or lower(coalesce(dm.motifEvoque, '')) like lower(concat('%', :motif, '%')))
""")
    Page<DemandeMere> searchBackoffice(
            @Param("num")          String num,
            @Param("demandeur")    String demandeur,
            @Param("type")         String type,
            @Param("priorite")     String priorite,
            @Param("motif")        String motif,
            @Param("roleStatuts")  List<Integer> roleStatuts,
            @Param("myStatut")     Integer myStatut,
            @Param("myVisibleIds") List<Integer> myVisibleIds,
            @Param("dateFrom")     LocalDateTime dateFrom,
            @Param("dateTo")       LocalDateTime dateTo,
            Pageable pageable
    );

}
