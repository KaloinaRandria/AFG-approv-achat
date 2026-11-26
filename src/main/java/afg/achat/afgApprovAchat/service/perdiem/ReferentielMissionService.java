package afg.achat.afgApprovAchat.service.perdiem;

import afg.achat.afgApprovAchat.model.perdiem.CategoriePersonnel;
import afg.achat.afgApprovAchat.model.perdiem.ZoneMission;
import afg.achat.afgApprovAchat.repository.perdiem.CategoriePersonnelRepo;
import afg.achat.afgApprovAchat.repository.perdiem.ZoneMissionRepo;
import afg.achat.afgApprovAchat.service.securite.Habilitations;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Référentiels du barème des per diem : catégories de personnel et zones de mission.
 *
 * <p>Tenus par le RH MAKER (ou l'administrateur). Rien n'est supprimé : un élément désactivé
 * n'est plus proposé, mais le barème et les missions qui y renvoient le conservent. Le code,
 * dérivé du libellé à la création, ne change plus ensuite.</p>
 */
@Service
@RequiredArgsConstructor
public class ReferentielMissionService {

    private static final int LONGUEUR_CODE = 50;

    private final CategoriePersonnelRepo categorieRepo;
    private final ZoneMissionRepo zoneRepo;

    // ── Lecture ──────────────────────────────────────────────────────────────

    public List<CategoriePersonnel> categories() {
        return categorieRepo.findAllOrdonnees();
    }

    public List<CategoriePersonnel> categoriesActives() {
        return categorieRepo.findActives();
    }

    public List<ZoneMission> zones() {
        return zoneRepo.findAllOrdonnees();
    }

    public List<ZoneMission> zonesActives() {
        return zoneRepo.findActives();
    }

    public CategoriePersonnel categorie(Integer id) {
        if (id == null) throw new IllegalArgumentException("La catégorie de personnel est obligatoire.");
        return categorieRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie de personnel introuvable."));
    }

    public ZoneMission zone(Integer id) {
        if (id == null) throw new IllegalArgumentException("La zone est obligatoire.");
        return zoneRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone introuvable."));
    }

    // ── Catégories de personnel ──────────────────────────────────────────────

    @Transactional
    public CategoriePersonnel creerCategorie(Authentication auth, String libelle, String ordre) {
        verifierDroit(auth);
        String l = libelleValide(libelle);
        if (categorieRepo.existsByLibelleIgnoreCase(l)) {
            throw new IllegalArgumentException("La catégorie « " + l + " » existe déjà.");
        }
        CategoriePersonnel c = new CategoriePersonnel();
        c.setCode(codeLibre(l, "CATEGORIE", categorieRepo::existsByCodeIgnoreCase));
        c.setLibelle(l);
        c.setOrdre(lireOrdre(ordre));
        c.setActif(true);
        return categorieRepo.save(c);
    }

    @Transactional
    public CategoriePersonnel modifierCategorie(Authentication auth, int id, String libelle, String ordre) {
        verifierDroit(auth);
        CategoriePersonnel c = categorie(id);
        String l = libelleValide(libelle);
        if (categorieRepo.existsByLibelleIgnoreCaseAndIdNot(l, id)) {
            throw new IllegalArgumentException("La catégorie « " + l + " » existe déjà.");
        }
        c.setLibelle(l);
        c.setOrdre(lireOrdre(ordre));
        return categorieRepo.save(c);
    }

    @Transactional
    public CategoriePersonnel activerCategorie(Authentication auth, int id, boolean actif) {
        verifierDroit(auth);
        CategoriePersonnel c = categorie(id);
        c.setActif(actif);
        return categorieRepo.save(c);
    }

    // ── Zones de mission ─────────────────────────────────────────────────────

    @Transactional
    public ZoneMission creerZone(Authentication auth, String libelle, boolean etranger, String ordre) {
        verifierDroit(auth);
        String l = libelleValide(libelle);
        if (zoneRepo.existsByLibelleIgnoreCase(l)) {
            throw new IllegalArgumentException("La zone « " + l + " » existe déjà.");
        }
        ZoneMission z = new ZoneMission();
        z.setCode(codeLibre(l, "ZONE", zoneRepo::existsByCodeIgnoreCase));
        z.setLibelle(l);
        z.setEtranger(etranger);
        z.setOrdre(lireOrdre(ordre));
        z.setActif(true);
        return zoneRepo.save(z);
    }

    @Transactional
    public ZoneMission modifierZone(Authentication auth, int id, String libelle, boolean etranger, String ordre) {
        verifierDroit(auth);
        ZoneMission z = zone(id);
        String l = libelleValide(libelle);
        if (zoneRepo.existsByLibelleIgnoreCaseAndIdNot(l, id)) {
            throw new IllegalArgumentException("La zone « " + l + " » existe déjà.");
        }
        z.setLibelle(l);
        z.setEtranger(etranger);
        z.setOrdre(lireOrdre(ordre));
        return zoneRepo.save(z);
    }

    @Transactional
    public ZoneMission activerZone(Authentication auth, int id, boolean actif) {
        verifierDroit(auth);
        ZoneMission z = zone(id);
        z.setActif(actif);
        return zoneRepo.save(z);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Gestion des référentiels : RH + MAKER, ou administrateur. */
    public static boolean peutGerer(Authentication auth) {
        return Habilitations.peutCreer(auth, Habilitations.RH);
    }

    private static void verifierDroit(Authentication auth) {
        if (!peutGerer(auth)) {
            throw new AccessDeniedException("Réservé aux RH (MAKER) et à l'administrateur.");
        }
    }

    static String libelleValide(String libelle) {
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
    static Integer lireOrdre(String ordre) {
        if (ordre == null || ordre.isBlank()) return null;
        try {
            int o = Integer.parseInt(ordre.trim());
            if (o < 0) throw new NumberFormatException();
            return o;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("L'ordre d'affichage doit être un nombre entier positif.");
        }
    }

    /** « Chef-lieu de province » → CHEF_LIEU_DE_PROVINCE, suffixé s'il est déjà pris. */
    static String codeLibre(String libelle, String parDefaut, Predicate<String> dejaPris) {
        String base = Normalizer.normalize(libelle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = parDefaut;
        if (base.length() > LONGUEUR_CODE - 3) base = base.substring(0, LONGUEUR_CODE - 3);

        String code = base;
        for (int n = 2; dejaPris.test(code); n++) {
            code = base + "_" + n;
        }
        return code;
    }
}
