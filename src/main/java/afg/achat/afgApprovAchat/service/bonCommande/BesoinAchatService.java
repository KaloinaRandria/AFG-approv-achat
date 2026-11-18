package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Besoins d'achat : lignes de demandes de matériel validées qui ne sont couvertes ni par une sortie de
 * stock ni par un bon de commande.
 *
 * <p>Reste à commander = quantité demandée − quantité sortie (bons de sortie confirmés)
 * − quantité commandée (bons de commande non annulés), jamais négatif.</p>
 *
 * <p>Limite connue : la réception d'un bon de livraison n'est pas encore rattachée au bon de
 * commande. Une quantité commandée, puis reçue et sortie pour la même ligne, est donc comptée
 * à la fois comme commandée et comme sortie : le reste à commander peut être sous-estimé si
 * l'on a commandé moins que le besoin. La fiche de la demande affiche toujours le restant à
 * sortir exact (quantité demandée − quantité sortie).</p>
 */
@Service
@RequiredArgsConstructor
public class BesoinAchatService {

    /** Une ligne de demande avec ce qui la couvre déjà. */
    public record BesoinAchat(DemandeFille ligne, double quantiteDemandee, double quantiteSortie,
                              double quantiteCommandee) {
        public double getResteACommander() {
            return resteACommander(quantiteDemandee, quantiteSortie, quantiteCommandee);
        }
        public DemandeFille getLigne() { return ligne; }
        public double getQuantiteDemandee() { return quantiteDemandee; }
        public double getQuantiteSortie() { return quantiteSortie; }
        public double getQuantiteCommandee() { return quantiteCommandee; }
    }

    private final DemandeFilleRepo demandeFilleRepo;
    private final BonSortieFilleRepo bonSortieFilleRepo;
    private final BonCommandeFilleRepo bonCommandeFilleRepo;

    public static double resteACommander(double demandee, double sortie, double commandee) {
        return Math.max(0, demandee - sortie - commandee);
    }

    /** Toutes les lignes de demandes validées qui ont encore un reste à commander. */
    public List<BesoinAchat> lignesEnAttente() {
        List<DemandeFille> lignes = demandeFilleRepo.findLignesAchetables(StatutDemande.VALIDE, StatutDemande.REFUSE);
        return besoins(lignes).stream()
                .filter(b -> b.getResteACommander() > 0)
                .toList();
    }

    /** Besoins des lignes données (avec ou sans reste à commander), dans le même ordre. */
    public List<BesoinAchat> besoins(Collection<DemandeFille> lignes) {
        if (lignes.isEmpty()) return List.of();
        List<DemandeFille> liste = List.copyOf(lignes);
        Map<Integer, Double> sorties = enMap(bonSortieFilleRepo.quantitesSortiesParLigne(liste));
        Map<Integer, Double> commandees = enMap(bonCommandeFilleRepo.quantitesCommandeesParLigne(liste));
        return liste.stream()
                .map(df -> new BesoinAchat(df, df.getQuantite(),
                        sorties.getOrDefault(df.getId(), 0.0),
                        commandees.getOrDefault(df.getId(), 0.0)))
                .toList();
    }

    /**
     * Reste à commander pour une ligne, sans compter le bon de commande en cours de saisie
     * (ses propres lignes sont remplacées à l'enregistrement).
     *
     * @param bcEnCours identifiant du bon en cours de saisie ; {@code null} pour un nouveau bon
     */
    public double resteACommander(DemandeFille ligne, String bcEnCours) {
        double sortie = bonSortieFilleRepo.sumQuantiteSortieByDemandeFille(ligne);
        double commandee = bonCommandeFilleRepo.quantiteCommandeeHorsBon(ligne, bcEnCours == null ? "" : bcEnCours);
        return resteACommander(ligne.getQuantite(), sortie, commandee);
    }

    private static Map<Integer, Double> enMap(List<Object[]> lignes) {
        Map<Integer, Double> map = new HashMap<>();
        for (Object[] r : lignes) {
            map.put((Integer) r[0], ((Number) r[1]).doubleValue());
        }
        return map;
    }
}
