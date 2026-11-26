package afg.achat.afgApprovAchat.service.util;

import org.springframework.stereotype.Service;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class FormatNumber {

    public static String format(double number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');

        DecimalFormat formatter = new DecimalFormat("#,###", symbols);
        return formatter.format(number);
    }

    public static String formatDecimal(Double number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.'); // ou ',' si tu veux le style français

        DecimalFormat formatter = new DecimalFormat("#,###.00", symbols);
        return formatter.format(number);
    }

    /**
     * Quantité d'articles : sans décimales quand elle est entière (« 20 », « 1 250 »),
     * avec au plus 2 décimales sinon (« 2,5 »). Vide si la quantité n'est pas renseignée.
     */
    public static String quantite(Double quantite) {
        if (quantite == null) return "";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('-');
        return new DecimalFormat("#,##0.##", symbols).format(quantite);
    }

    /**
     * Montant suivi du code de sa devise : « 1 250 000,00 MGA », « 3 000,50 EUR ».
     * Le nombre de décimales suit la norme ISO 4217 (2 pour la plupart des devises).
     * Vide si le montant n'est pas renseigné.
     */
    public static String montant(Double montant, String codeDevise) {
        if (montant == null) return "";
        String code = codeDevise == null || codeDevise.isBlank() ? "MGA" : codeDevise.trim().toUpperCase();
        return nombre(montant, decimales(code)) + " " + code;
    }

    /**
     * Cours d'une devise en ariary, sans le code : 2 décimales (« 5 029,58 »), jusqu'à 4 sous
     * 100 MGA pour les petites devises (« 7,6675 » pour le XOF). Vide si non renseigné.
     */
    public static String cours(Double cours) {
        if (cours == null) return "";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('-');
        return new DecimalFormat(Math.abs(cours) < 100 ? "#,##0.00##" : "#,##0.00", symbols).format(cours);
    }

    /** Montant sans le code de la devise, avec les décimales de la devise. */
    public static String nombre(Double montant, String codeDevise) {
        if (montant == null) return "";
        return nombre(montant, decimales(codeDevise == null ? "MGA" : codeDevise.trim().toUpperCase()));
    }

    private static String nombre(double montant, int decimales) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('-');
        String motif = decimales <= 0 ? "#,##0" : "#,##0." + "0".repeat(decimales);
        return new DecimalFormat(motif, symbols).format(montant);
    }

    /** Décimales de la devise selon ISO 4217 ; 2 pour un code inconnu. */
    static int decimales(String codeDevise) {
        try {
            int d = java.util.Currency.getInstance(codeDevise).getDefaultFractionDigits();
            return d < 0 ? 2 : d;
        } catch (IllegalArgumentException | NullPointerException e) {
            return 2;
        }
    }

    public static String formatDate() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        return date.format(formatter);
    }

}

