package afg.achat.afgApprovAchat.repository.stock;

import afg.achat.afgApprovAchat.model.stock.LotStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LotStockRepo extends JpaRepository<LotStock, Long> {

    // Lots disponibles triés FIFO (plus ancien en premier)
    @Query("""
        SELECT l FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
        ORDER BY l.dateEntree ASC
    """)
    List<LotStock> findLotsDisponibles(@Param("code") String code);

    // Stock total disponible
    @Query("""
        SELECT COALESCE(SUM(l.quantiteRestante), 0)
        FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
    """)
    double getStockDisponible(@Param("code") String code);

    /** Stock disponible de plusieurs articles : [id article, quantité] (articles sans stock absents). */
    @Query("""
        SELECT l.article.id, SUM(l.quantiteRestante)
        FROM LotStock l
        WHERE l.article.id IN :ids
        AND l.quantiteRestante > 0
        GROUP BY l.article.id
    """)
    List<Object[]> stocksDisponibles(@Param("ids") java.util.Collection<Integer> ids);

    /** Dernier lot reçu à un prix connu (valorisation d'un écart d'inventaire positif). */
    java.util.Optional<LotStock> findFirstByArticle_IdAndPrixUnitaireGreaterThanOrderByDateEntreeDesc(int articleId, double prix);

    /** Stock disponible : [valeur au coût des lots, nombre d'articles en stock]. */
    @Query("""
        SELECT COALESCE(SUM(l.quantiteRestante * l.prixUnitaire), 0), COUNT(DISTINCT l.article.id)
        FROM LotStock l
        WHERE l.quantiteRestante > 0
    """)
    List<Object[]> valeurStock();

    /**
     * Articles dormants : en stock depuis avant {@code depuis} et sans aucune sortie depuis
     * cette date. [nombre d'articles, valeur immobilisée].
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*), COALESCE(SUM(v.valeur), 0)
        FROM (SELECT l.id_article, SUM(l.quantite_restante * l.prix_unitaire) AS valeur,
                     MIN(l.date_entree) AS premiere_entree
              FROM lot_stock l
              WHERE l.quantite_restante > 0
              GROUP BY l.id_article) v
        WHERE v.premiere_entree < :depuis
          AND NOT EXISTS (SELECT 1 FROM v_historique_mouvement_stock h
                          WHERE h.id_article = v.id_article
                            AND h.type_mouvement = 'SORTIE'
                            AND h.date_mouvement >= :depuis)
    """)
    List<Object[]> articlesDormants(@Param("depuis") java.time.LocalDateTime depuis);

    // Valeur totale du stock (pour reporting)
    @Query("""
        SELECT COALESCE(SUM(l.quantiteRestante * l.prixUnitaire), 0)
        FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
    """)
    double getValeurTotaleStock(@Param("code") String code);

    // Prix FIFO simulé pour une quantité donnée
    @Query("""
        SELECT l FROM LotStock l
        WHERE l.article.codeArticle = :code
        AND l.quantiteRestante > 0
        ORDER BY l.dateEntree ASC
    """)
    List<LotStock> findLotsOrdonnes(@Param("code") String code);
}
