package afg.achat.afgApprovAchat.model.util;

import java.util.List;

public final class MontantCalculator {

    private MontantCalculator() {
        // empêche l'instanciation
    }

    /**
     * Calcule le montant total à partir des quantités et prix unitaires
     */
    public static double calculerTotal(List<String> quantites, List<String> prixUnitaires) {
        if (quantites == null || prixUnitaires == null) return 0;

        int size = Math.min(quantites.size(), prixUnitaires.size());
        double total = 0;

        for (int i = 0; i < size; i++) {
            double qte = parseDoubleSafe(quantites.get(i));
            double prix = parseDoubleSafe(prixUnitaires.get(i));
            total += qte * prix;
        }

        return total;
    }

    /**
     * Parse sécurisé (évite NumberFormatException)
     */
    private static double parseDoubleSafe(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (Exception e) {
            return 0;
        }
    }
}
