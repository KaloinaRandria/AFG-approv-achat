package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.service.bonCommande.LigneImprimee;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ligne à réceptionner telle que le fournisseur la livre.
 *
 * <p>Un bon de commande groupé garde une ligne par ligne de demande (ex. 9 et 11 cahiers),
 * fusionnées sur le bon imprimé (20 cahiers). La réception suit le bon imprimé : une seule
 * saisie pour le groupe, puis la quantité reçue est répartie entre les lignes, dans leur ordre
 * de création (la demande la plus ancienne est servie en premier).</p>
 *
 * @param lignes lignes du bon de commande du groupe, dans leur ordre de création
 */
public record GroupeReception(List<BonCommandeFille> lignes) {

    /** Regroupe les lignes comme le bon imprimé (même article, désignation, unité et prix). */
    public static List<GroupeReception> grouper(List<BonCommandeFille> lignesBc) {
        List<BonCommandeFille> triees = lignesBc.stream()
                .sorted(Comparator.comparingInt(BonCommandeFille::getId))
                .toList();
        return LigneImprimee.regrouper(triees).stream()
                .map(l -> new GroupeReception(l.details()))
                .toList();
    }

    /** Identifiant du groupe dans le formulaire : celui de sa première ligne. */
    public int getId() {
        return lignes.get(0).getId();
    }

    public Article getArticle() {
        return lignes.get(0).getArticle();
    }

    public String getDesignation() {
        return lignes.get(0).getDesignationAffichee();
    }

    public double getPrixUnitaireHT() {
        return lignes.get(0).getPrixUnitaireHT();
    }

    public double getQuantiteCommandee() {
        return lignes.stream().mapToDouble(BonCommandeFille::getQuantiteCommandee).sum();
    }

    public double getQuantiteRecue() {
        return lignes.stream().mapToDouble(BonCommandeFille::getQuantiteRecue).sum();
    }

    public double getResteARecevoir() {
        return lignes.stream().mapToDouble(BonCommandeFille::getResteARecevoir).sum();
    }

    public boolean isFusionnee() {
        return lignes.size() > 1;
    }

    /**
     * Répartit une quantité reçue entre les lignes du groupe : chaque ligne reçoit au plus son
     * reste, dans l'ordre. Le résultat a la taille du groupe ; la somme vaut {@code quantite}
     * tant qu'elle ne dépasse pas le reste du groupe.
     */
    public List<Double> repartir(double quantite) {
        List<Double> parts = new ArrayList<>();
        double aRepartir = quantite;
        for (BonCommandeFille ligne : lignes) {
            double part = Math.min(ligne.getResteARecevoir(), Math.max(aRepartir, 0));
            parts.add(part);
            aRepartir -= part;
        }
        return parts;
    }
}
