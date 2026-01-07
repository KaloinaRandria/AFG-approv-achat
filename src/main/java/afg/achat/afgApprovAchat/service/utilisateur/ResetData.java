package afg.achat.afgApprovAchat.service.utilisateur;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class ResetData {
    private final JdbcTemplate jdbcTemplate;

    public ResetData(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void initBase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        jdbcTemplate.execute("TRUNCATE TABLE article");
        jdbcTemplate.execute("TRUNCATE TABLE fournisseur");
        jdbcTemplate.execute("TRUNCATE TABLE famille");
        jdbcTemplate.execute("TRUNCATE TABLE article_historique");
        jdbcTemplate.execute("TRUNCATE TABLE udm");


        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

    }
}
