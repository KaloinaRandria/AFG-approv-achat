package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    /** Critères de l'écran « Lignes à acheter » ; les champs vides ou {@code null} sont ignorés. */
    public record Recherche(String demande, String demandeur, String article, String motif,
                            LocalDate du, LocalDate au) {
    }

    /**
     * Lignes de demandes de matériel validées qui ont encore un reste à commander, filtrées et
     * paginées par la base (le reste à commander est calculé dans la requête).
     */
    public Page<BesoinAchat> lignesEnAttente(Recherche r, Pageable pageable) {
        Page<DemandeFille> page = demandeFilleRepo.rechercherLignesAAcheter(StatutDemande.VALIDE, StatutDemande.REFUSE,
                r.du() == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : r.du().atStartOfDay(),
                r.au() == null ? LocalDateTime.of(2999, 12, 31, 23, 59, 59) : r.au().atTime(LocalTime.MAX),
                texte(r.demande()), texte(r.demandeur()), texte(r.article()), texte(r.motif()), pageable);
        // Détail des quantités (sorti, commandé) pour les seules lignes de la page
        return new PageImpl<>(besoins(page.getContent()), pageable, page.getTotalElements());
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur.trim();
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
