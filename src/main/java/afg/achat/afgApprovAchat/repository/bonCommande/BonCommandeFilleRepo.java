package afg.achat.afgApprovAchat.repository.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonCommandeFilleRepo extends JpaRepository<BonCommandeFille, Integer> {
    @Query("select bcf from BonCommandeFille bcf where bcf.bonCommandeMere = :b")
    List<BonCommandeFille> findBonCommandeFillesByBonCommandeMere(BonCommandeMere b);

    void deleteByBonCommandeMere(BonCommandeMere bonCommandeMere);

    /** Pour chaque ligne de demande : [id de la ligne, quantité commandée sur les bons non annulés]. */
    @Query("""
        SELECT f.demandeFille.id, COALESCE(SUM(f.quantiteCommandee), 0)
        FROM BonCommandeFille f
        WHERE f.demandeFille IN :lignes
          AND f.bonCommandeMere.statut <> afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande.ANNULE
        GROUP BY f.demandeFille.id
    """)
    List<Object[]> quantitesCommandeesParLigne(@Param("lignes") List<DemandeFille> lignes);

    /** Quantité commandée pour une ligne de demande sur les bons non annulés, hors le bon indiqué. */
    @Query("""
        SELECT COALESCE(SUM(f.quantiteCommandee), 0)
        FROM BonCommandeFille f
        WHERE f.demandeFille = :ligne
          AND f.bonCommandeMere.statut <> afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande.ANNULE
          AND f.bonCommandeMere.id <> :bcExclu
    """)
    double quantiteCommandeeHorsBon(@Param("ligne") DemandeFille ligne, @Param("bcExclu") String bcExclu);

    /** Lignes de bons non annulés qui portent sur les lignes d'une demande. */
    @Query("""
        SELECT f FROM BonCommandeFille f
        WHERE f.demandeFille.demandeMere = :demande
          AND f.bonCommandeMere.statut <> afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande.ANNULE
        ORDER BY f.bonCommandeMere.dateCreation DESC
    """)
    List<BonCommandeFille> lignesCommandeesDeLaDemande(@Param("demande") DemandeMere demande);

    /** Bons de commande (tous statuts) qui contiennent au moins une ligne de la demande. */
    @Query("""
        SELECT DISTINCT f.bonCommandeMere FROM BonCommandeFille f
        WHERE f.demandeFille.demandeMere = :demande
    """)
    List<BonCommandeMere> bonsContenantLaDemande(@Param("demande") DemandeMere demande);
}
