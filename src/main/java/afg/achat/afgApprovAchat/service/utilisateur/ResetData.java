package afg.achat.afgApprovAchat.service.utilisateur;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class ResetData {

    private static final Logger log = LoggerFactory.getLogger(ResetData.class);
    private final JdbcTemplate jdbcTemplate;

    public ResetData(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void initBase() {
        log.info("=== RESET DATA START (PostgreSQL) ===");

        try {
            // IMPORTANT : mettre toutes les tables concernées
            String sql = """
                TRUNCATE TABLE
                    bon_livraison_fille,
                    bon_livraison_mere,
                    demande_fille,
                    demande_mere,
                    stock_fille,
                    stock_mere,
                    article_historique,
                    article,
                    fournisseur,
                    famille,
                    udm
                RESTART IDENTITY CASCADE
            """;

            log.info("Executing TRUNCATE ... RESTART IDENTITY CASCADE");
            jdbcTemplate.execute(sql);

            log.info("=== RESET DATA DONE ===");
        } catch (Exception e) {
            log.error("=== RESET DATA FAILED ===", e);
            throw e;
        }
    }

    private void truncate(String table) {
        log.info("Truncating table: {}", table);
        jdbcTemplate.execute("TRUNCATE TABLE " + table);
    }

    // Optionnel: utile seulement si tu fais DELETE au lieu de TRUNCATE
    @SuppressWarnings("unused")
    private void resetAutoIncrement(String table, int startWith) {
        log.info("Reset AUTO_INCREMENT for {} to {}", table, startWith);
        jdbcTemplate.execute("ALTER TABLE " + table + " AUTO_INCREMENT = " + startWith);
    }
}
