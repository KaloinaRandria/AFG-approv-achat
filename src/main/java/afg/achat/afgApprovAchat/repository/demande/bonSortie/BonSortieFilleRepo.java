package afg.achat.afgApprovAchat.repository.demande.bonSortie;

import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieMere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonSortieFilleRepo extends JpaRepository<BonSortieFille, Integer> {
    List<BonSortieFille> findByBonSortieMere(BonSortieMere bonSortieMere);

    // Somme des quantités déjà sorties pour un article donné
    @Query("""
        select coalesce(sum(f.quantiteSortie), 0)
        from BonSortieFille f
        where f.article.codeArticle = :codeArticle
          and f.statut = 'SORTIE'
    """)
    double sumQuantiteSortieByArticle(@Param("codeArticle") String codeArticle);

}
