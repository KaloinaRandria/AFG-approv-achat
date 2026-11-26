package afg.achat.afgApprovAchat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Vérifie que les tables de la base correspondent aux entités JPA ({@code ddl-auto=validate}),
 * sans démarrer l'application : seule la couche JPA est chargée, en connexion lecture seule.
 * Un écart fait échouer le chargement du contexte avec un message
 * « Schema-validation: missing table / missing column / wrong column type ».
 *
 * <p>Interroge la vraie base (celle de application.properties, ou SPRING_DATASOURCE_URL) :
 * ignoré par défaut, à lancer explicitement :
 * <pre>.\mvnw test "-Dtest=SchemaValidationTest" "-Dschema.validation=true"</pre>
 */
@EnabledIfSystemProperty(named = "schema.validation", matches = "true")
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.read-only=true",
        "spring.jpa.properties.hibernate.search.enabled=false",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaValidationTest {

    @Test
    void leSchemaCorrespondAuxEntites() {
        // La validation a lieu au démarrage de l'EntityManagerFactory : arriver ici suffit.
    }
}
