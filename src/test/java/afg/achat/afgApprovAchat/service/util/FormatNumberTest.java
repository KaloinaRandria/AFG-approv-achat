package afg.achat.afgApprovAchat.service.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void montantAvecLeCodeEtLesDecimalesDeLaDevise() {
        assertEquals("3 000,50 EUR", FormatNumber.montant(3000.5, "EUR"));
        assertEquals("1 250 000" + decimalesMga() + " MGA", FormatNumber.montant(1_250_000.0, "MGA"));
        assertEquals("1 250 000" + decimalesMga(), FormatNumber.nombre(1_250_000.0, "MGA"));
    }

    @Test
    void sansDeviseCestLAriaryEtSansMontantRienNestAffiche() {
        assertTrue(FormatNumber.montant(5.0, null).endsWith(" MGA"));
        assertEquals("", FormatNumber.montant(null, "EUR"));
    }

    @Test
    void codeInconnuDeuxDecimales() {
        assertEquals("12,30 XYZ", FormatNumber.montant(12.3, "xyz"));
    }

    /** Décimales de l'ariary selon la table ISO 4217 du JDK. */
    private static String decimalesMga() {
        int d = FormatNumber.decimales("MGA");
        return d == 0 ? "" : "," + "0".repeat(d);
    }
}
