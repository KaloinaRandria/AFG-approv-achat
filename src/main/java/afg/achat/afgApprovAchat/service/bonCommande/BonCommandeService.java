package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BonCommandeService {
    private final BonCommandeMereRepo bonCommandeMereRepo;
    private final BonCommandeFilleRepo bonCommandeFilleRepo;
    private final IdGenerator idGenerator;

    public List<BonCommandeFille> getBonCommandeFillesByBonCommandeMere(BonCommandeMere b) {
        return bonCommandeFilleRepo.findBonCommandeFillesByBonCommandeMere(b);
    }

    public BonCommandeMere saveBonCommandeMere(BonCommandeMere bonCommandeMere) {
        // Générer l'ID si non défini
        if (bonCommandeMere.getId() == null || bonCommandeMere.getId().isEmpty()) {
            bonCommandeMere.setId(idGenerator);
        }
        return bonCommandeMereRepo.save(bonCommandeMere);
    }

    public BonCommandeFille saveBonCommandeFille(BonCommandeFille bonCommandeFille) {
        return bonCommandeFilleRepo.save(bonCommandeFille);
    }

    public List<BonCommandeMere> getBonCommandesByDemande(DemandeMere demandeMere) {
        return bonCommandeMereRepo.findByDemandeMereOrderByDateCreationDesc(demandeMere);
    }

    public void deleteBonCommandeFillesByBonCommandeMere(BonCommandeMere bonCommandeMere) {
        bonCommandeFilleRepo.deleteByBonCommandeMere(bonCommandeMere);
    }

    /**
     * Bons de commande d'une demande, du plus récent au plus ancien : ceux créés depuis la
     * demande et les bons groupés qui contiennent au moins une de ses lignes.
     */
    public List<BonCommandeMere> getBonsCommandeDeLaDemande(DemandeMere demande) {
        Map<String, BonCommandeMere> bons = new LinkedHashMap<>();
        bonCommandeMereRepo.findByDemandeMereOrderByDateCreationDesc(demande).forEach(bc -> bons.put(bc.getId(), bc));
        bonCommandeFilleRepo.bonsContenantLaDemande(demande).forEach(bc -> bons.putIfAbsent(bc.getId(), bc));
        return bons.values().stream()
                .sorted(Comparator.comparing(BonCommandeMere::getDateCreation,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Lignes des bons de commande non annulés qui portent sur les lignes d'une demande. */
    public List<BonCommandeFille> getLignesCommandeesDeLaDemande(DemandeMere demande) {
        return bonCommandeFilleRepo.lignesCommandeesDeLaDemande(demande);
    }

    /** Critères de la liste des bons de commande ; les champs vides ou {@code null} sont ignorés. */
    public record Recherche(BonCommandeMere.StatutBonCommande statut,
                            boolean aValiderSeulement,
                            List<BonCommandeMere.EtapeValidation> etapesDuValideur,
                            LocalDate du, LocalDate au,
                            String numero, String fournisseur, String objet,
                            String demande, String article,
                            boolean aRecevoirSeulement) {

        /** Sans le filtre « à réceptionner ». */
        public Recherche(BonCommandeMere.StatutBonCommande statut, boolean aValiderSeulement,
                         List<BonCommandeMere.EtapeValidation> etapesDuValideur, LocalDate du, LocalDate au,
                         String numero, String fournisseur, String objet, String demande, String article) {
            this(statut, aValiderSeulement, etapesDuValideur, du, au, numero, fournisseur, objet, demande, article, false);
        }
    }

    /** Réception pas encore terminée : le bon validé attend (encore) une livraison. */
    public static final List<BonCommandeMere.StatutReception> RECEPTIONS_OUVERTES =
            List.of(BonCommandeMere.StatutReception.NON_RECU, BonCommandeMere.StatutReception.PARTIELLEMENT_RECU);

    /**
     * Recherche paginée des bons de commande.
     * « À valider » ne garde que les bons en attente à une étape que l'utilisateur peut valider.
     */
    public Page<BonCommandeMere> rechercher(Recherche r, Pageable pageable) {
        List<BonCommandeMere.StatutBonCommande> statuts;
        boolean filtrerEtape = false;
        List<BonCommandeMere.EtapeValidation> etapes = List.of(BonCommandeMere.EtapeValidation.values());

        if (r.aValiderSeulement()) {
            statuts = List.of(BonCommandeMere.StatutBonCommande.EN_ATTENTE_VALIDATION);
            filtrerEtape = true;
            // L'utilisateur ne valide aucune étape : rien à lui afficher
            if (r.etapesDuValideur() == null || r.etapesDuValideur().isEmpty()) {
                return Page.empty(pageable);
            }
            etapes = r.etapesDuValideur();
        } else if (r.aRecevoirSeulement()) {
            // À réceptionner : bons validés dont la réception n'est pas terminée
            statuts = List.of(BonCommandeMere.StatutBonCommande.VALIDE);
        } else {
            statuts = r.statut() != null ? List.of(r.statut()) : List.of(BonCommandeMere.StatutBonCommande.values());
        }

        return bonCommandeMereRepo.rechercher(statuts, filtrerEtape, etapes,
                r.du() == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : r.du().atStartOfDay(),
                r.au() == null ? LocalDateTime.of(2999, 12, 31, 23, 59, 59) : r.au().atTime(LocalTime.MAX),
                texte(r.numero()), texte(r.fournisseur()), texte(r.objet()),
                texte(r.demande()), texte(r.article()),
                r.aRecevoirSeulement(), RECEPTIONS_OUVERTES, pageable);
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur.trim();
    }

    public List<BonCommandeMere> getAllBonCommandes() {
        return bonCommandeMereRepo.findAllByOrderByDateCreationDesc();
    }

    public Optional<BonCommandeMere> getBonCommandeMereById(String id) {
        return bonCommandeMereRepo.findById(id);
    }
}
