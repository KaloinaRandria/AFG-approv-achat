package afg.achat.afgApprovAchat.repository.stock.inventaire;

import afg.achat.afgApprovAchat.model.stock.inventaire.InventaireLigne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventaireLigneRepo extends JpaRepository<InventaireLigne, Integer> {

    /**
     * Synthèse par inventaire : [id inventaire, lignes, lignes comptées, lignes en écart,
     * valeur des écarts positifs, valeur des écarts négatifs] (valeurs connues après validation).
     */
    @Query("""
        SELECT l.inventaire.id,
               COUNT(l),
               SUM(CASE WHEN l.quantiteComptee IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN l.quantiteComptee IS NOT NULL
                         AND ABS(l.quantiteComptee - l.stockTheorique) > 0.001 THEN 1 ELSE 0 END),
               SUM(CASE WHEN l.prixUnitaire IS NOT NULL AND l.quantiteComptee > l.stockTheorique
                        THEN (l.quantiteComptee - l.stockTheorique) * l.prixUnitaire ELSE 0 END),
               SUM(CASE WHEN l.prixUnitaire IS NOT NULL AND l.quantiteComptee < l.stockTheorique
                        THEN (l.quantiteComptee - l.stockTheorique) * l.prixUnitaire ELSE 0 END)
        FROM InventaireLigne l
        WHERE l.inventaire.id IN :ids
        GROUP BY l.inventaire.id
    """)
    List<Object[]> syntheses(@Param("ids") Collection<Integer> ids);
}
