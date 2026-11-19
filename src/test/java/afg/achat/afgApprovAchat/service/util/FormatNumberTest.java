package afg.achat.afgApprovAchat.service.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatNumberTest {

    @Test
    void quantiteEntiereSansDecimales() {
        assertEquals("20", FormatNumber.quantite(20.0));
        assertEquals("0", FormatNumber.quantite(0.0));
        assertEquals("1 250", FormatNumber.quantite(1250.0));
    }

    @Test
    void quantiteDecimaleGardeSesDecimales() {
        assertEquals("2,5", FormatNumber.quantite(2.5));
        assertEquals("0,75", FormatNumber.quantite(0.75));
        assertEquals("1 250,5", FormatNumber.quantite(1250.5));
    }

    @Test
    void quantiteNonRenseigneeVide() {
        assertEquals("", FormatNumber.quantite(null));
    }
}
