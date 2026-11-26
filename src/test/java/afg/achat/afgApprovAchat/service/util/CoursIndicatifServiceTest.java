package afg.achat.afgApprovAchat.service.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoursIndicatifServiceTest {

    private static JsonNode json(String s) throws Exception {
        return new ObjectMapper().readTree(s);
    }

    private static final String REPONSE = """
            {"result":"success","provider":"https://www.exchangerate-api.com",
             "time_last_update_unix":1795564951,
             "rates":{"USD":1,"MGA":4580.5,"EUR":0.92}}
            """;

    @Test
    void coursCalculeDepuisLaBaseDollar() throws Exception {
        var c = CoursIndicatifService.calculer("EUR", json(REPONSE));
        assertEquals("EUR", c.devise());
        assertEquals(4978.8043, c.coursAriary(), 0.00001); // 4580.5 / 0.92 arrondi à 4 décimales
        assertEquals("https://www.exchangerate-api.com", c.source());
        assertNotNull(c.dateSource());
    }

    @Test
    void coursDuDollar() throws Exception {
        assertEquals(4580.5, CoursIndicatifService.calculer("USD", json(REPONSE)).coursAriary(), 0.001);
    }

    @Test
    void deviseInconnueDeLaSource() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> CoursIndicatifService.calculer("XYZ", json(REPONSE)));
    }

    @Test
    void ariaryAbsentDeLaSource() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> CoursIndicatifService.calculer("EUR", json("{\"rates\":{\"USD\":1,\"EUR\":0.92}}")));
    }

    @Test
    void codeInvalideRefuseSansAppelReseau() {
        var service = new CoursIndicatifService("http://127.0.0.1:1/", 1, 60, "", 0);
        assertThrows(IllegalArgumentException.class, () -> service.coursIndicatif("EU"));
    }

    @Test
    void sourceInjoignable() {
        var service = new CoursIndicatifService("http://127.0.0.1:1/", 1, 60, "", 0);
        assertThrows(IllegalStateException.class, () -> service.coursIndicatif("EUR"));
    }
}
