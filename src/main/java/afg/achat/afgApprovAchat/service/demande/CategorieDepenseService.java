package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.CategorieDepense;
import afg.achat.afgApprovAchat.repository.demande.CategorieDepenseRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeAutreDetailRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paramétrage des catégories des demandes Autres (Moyens Généraux, administrateur).
 *
 * <p>Une catégorie n'est jamais supprimée : les demandes existantes y restent rattachées.
 * Désactivée, elle n'est plus proposée à la saisie. Son code, enregistré une fois pour toutes,
 * est dérivé du libellé à la création.</p>
 */
@Service
@RequiredArgsConstructor
public class CategorieDepenseService {

    private static final int LONGUEUR_CODE = 50;

    private final CategorieDepenseRepo categorieDepenseRepo;
    private final DemandeAutreDetailRepo demandeAutreDetailRepo;

    public List<CategorieDepense> getToutes() {
        return categorieDepenseRepo.findAllOrdonnees();
    }

    /** Nombre de demandes Autres par catégorie (clé : identifiant de la catégorie). */
    public Map<Integer, Long> nombreDeDemandesParCategorie() {
        Map<Integer, Long> m = new HashMap<>();
        for (Object[] r : demandeAutreDetailRepo.compterParCategorie()) {
            m.put((Integer) r[0], (Long) r[1]);
        }
        return m;
    }

    /** @throws IllegalArgumentException si le libellé est vide ou déjà pris */
    @Transactional
    public CategorieDepense creer(String libelle, boolean beneficiaireObligatoire, String ordre) {
        String l = libelleValide(libelle);
        if (categorieDepenseRepo.existsByLibelleIgnoreCase(l)) {
            throw new IllegalArgumentException("La catégorie « " + l + " » existe déjà.");
        }
        CategorieDepense c = new CategorieDepense();
        c.setCode(codeLibre(l));
        c.setLibelle(l);
        c.setBeneficiaireObligatoire(beneficiaireObligatoire);
        c.setOrdre(lireOrdre(ordre));
        c.setActif(true);
        return categorieDepenseRepo.save(c);
    }

    /** Le code ne change pas : il reste l'identifiant stable de la catégorie. */
    @Transactional
    public CategorieDepense modifier(int id, String libelle, boolean beneficiaireObligatoire, String ordre) {
        CategorieDepense c = lire(id);
        String l = libelleValide(libelle);
        if (categorieDepenseRepo.existsByLibelleIgnoreCaseAndIdNot(l, id)) {
            throw new IllegalArgumentException("La catégorie « " + l + " » existe déjà.");
        }
        c.setLibelle(l);
        c.setBeneficiaireObligatoire(beneficiaireObligatoire);
        c.setOrdre(lireOrdre(ordre));
        return categorieDepenseRepo.save(c);
    }

    @Transactional
    public CategorieDepense changerActivation(int id, boolean actif) {
        CategorieDepense c = lire(id);
        c.setActif(actif);
        return categorieDepenseRepo.save(c);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CategorieDepense lire(int id) {
        return categorieDepenseRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie introuvable."));
    }

    private static String libelleValide(String libelle) {
        String l = libelle == null ? "" : libelle.trim().replaceAll("\\s+", " ");
        if (l.isEmpty()) {
            throw new IllegalArgumentException("Le libellé est obligatoire.");
        }
        if (l.length() > 255) {
            throw new IllegalArgumentException("Le libellé ne doit pas dépasser 255 caractères.");
        }
        return l;
    }

    /** Ordre d'affichage facultatif : entier positif ou vide. */
    private static Integer lireOrdre(String ordre) {
        if (ordre == null || ordre.isBlank()) return null;
        try {
            int o = Integer.parseInt(ordre.trim());
            if (o < 0) throw new NumberFormatException();
            return o;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("L'ordre d'affichage doit être un nombre entier positif.");
        }
    }

    /** « Droits (divers / honoraires) » → DROITS_DIVERS_HONORAIRES, suffixé s'il est déjà pris. */
    private String codeLibre(String libelle) {
        String base = Normalizer.normalize(libelle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "CATEGORIE";
        if (base.length() > LONGUEUR_CODE - 3) base = base.substring(0, LONGUEUR_CODE - 3);

        String code = base;
        for (int n = 2; categorieDepenseRepo.existsByCodeIgnoreCase(code); n++) {
            code = base + "_" + n;
        }
        return code;
    }
}
