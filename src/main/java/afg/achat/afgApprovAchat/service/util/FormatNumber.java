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

    public static String formatDate() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        return date.format(formatter);
    }

}

