package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.DTO.ServiceDemandeDTO;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
            ORDER BY COUNT(dm) DESC
            """)
    List<ServiceDemandeDTO> countDemandesByService();
}