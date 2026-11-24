package afg.achat.afgApprovAchat.service.utilisateur;

import afg.achat.afgApprovAchat.model.util.Service;
import afg.achat.afgApprovAchat.model.utilisateur.Poste;
import afg.achat.afgApprovAchat.repository.util.ServiceRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.PosteRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.UtilisateurRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paramétrage de l'organisation : directions (entité {@link Service}) et postes,
 * rattachés aux utilisateurs.
 *
 * <p>Un libellé déjà pris est refusé, sans tenir compte des accents, de la casse ni des
 * espaces ; un élément n'est supprimé que s'il n'est attribué à aucun utilisateur.</p>
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class OrganisationService {

    private static final int LONGUEUR_ACRONYME = 20;

    private final ServiceRepo serviceRepo;
    private final PosteRepo posteRepo;
    private final UtilisateurRepo utilisateurRepo;

    // ── Directions ───────────────────────────────────────────────────────────

    public List<Service> getDirections() {
        return serviceRepo.findAll().stream()
                .sorted(Comparator.comparing(s -> cle(s.getLibelle())))
                .toList();
    }

    public Map<Integer, Long> nombreUtilisateursParDirection() {
        return compter(utilisateurRepo.compterParService());
    }

    /** @throws IllegalArgumentException si un champ est vide, ou si le libellé ou l'acronyme est déjà pris */
    @Transactional
    public Service creerDirection(String libelle, String acronyme) {
        Service s = new Service();
        remplirDirection(s, libelle, acronyme, null);
        return serviceRepo.save(s);
    }

    @Transactional
    public Service modifierDirection(int id, String libelle, String acronyme) {
        Service s = serviceRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Direction introuvable."));
        remplirDirection(s, libelle, acronyme, id);
        return serviceRepo.save(s);
    }

    @Transactional
    public Service supprimerDirection(int id) {
        Service s = serviceRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Direction introuvable."));
        long nb = utilisateurRepo.countByService_Id(id);
        if (nb > 0) {
            throw new IllegalArgumentException("La direction « " + s.getLibelle() + " » compte " + nb
                    + " utilisateur(s) : elle ne peut pas être supprimée.");
        }
        long nbPostes = nombrePostesParDirection().getOrDefault(id, 0L);
        if (nbPostes > 0) {
            throw new IllegalArgumentException("La direction « " + s.getLibelle() + " » a " + nbPostes
                    + " poste(s) rattaché(s) : détachez-les avant de la supprimer.");
        }
        serviceRepo.delete(s);
        serviceRepo.flush();
        return s;
    }

    private void remplirDirection(Service s, String libelle, String acronyme, Integer idExclu) {
        String l = libelleValide(libelle, "Le libellé de la direction");
        String a = acronyme == null ? "" : acronyme.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (a.isEmpty()) {
            throw new IllegalArgumentException("L'acronyme de la direction est obligatoire.");
        }
        if (a.length() > LONGUEUR_ACRONYME) {
            throw new IllegalArgumentException("L'acronyme ne doit pas dépasser " + LONGUEUR_ACRONYME + " caractères.");
        }
        for (Service autre : serviceRepo.findAll()) {
            if (idExclu != null && autre.getId() == idExclu) continue;
            if (cle(autre.getLibelle()).equals(cle(l))) {
                throw new IllegalArgumentException("La direction « " + autre.getLibelle() + " » existe déjà.");
            }
            if (autre.getAcronyme() != null && autre.getAcronyme().trim().equalsIgnoreCase(a)) {
                throw new IllegalArgumentException("L'acronyme « " + a + " » est déjà utilisé par « " + autre.getLibelle() + " ».");
            }
        }
        s.setLibelle(l);
        s.setAcronyme(a);
    }

    // ── Postes ───────────────────────────────────────────────────────────────

    public List<Poste> getPostes() {
        return posteRepo.findAll().stream()
                .sorted(Comparator.comparing(p -> cle(p.getPoste())))
                .toList();
    }

    public Map<Integer, Long> nombreUtilisateursParPoste() {
        return compter(utilisateurRepo.compterParPoste());
    }

    /** @param directionId direction de rattachement, facultative (vide : non rattaché) */
    @Transactional
    public Poste creerPoste(String libelle, String directionId) {
        String l = libelleValide(libelle, "Le libellé du poste");
        verifierPosteLibre(l, null);
        Poste p = new Poste(l);
        p.setDirection(lireDirection(directionId));
        return posteRepo.save(p);
    }

    /** Renommer corrige aussi les espaces en trop des libellés importés. */
    @Transactional
    public Poste modifierPoste(int id, String libelle, String directionId) {
        Poste p = posteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Poste introuvable."));
        String l = libelleValide(libelle, "Le libellé du poste");
        verifierPosteLibre(l, id);
        p.setPoste(l);
        p.setDirection(lireDirection(directionId));
        return posteRepo.save(p);
    }

    /**
     * Rattache plusieurs postes à une direction (ou les détache si {@code directionId} est vide).
     *
     * @return nombre de postes modifiés
     */
    @Transactional
    public int rattacherPostes(java.util.Collection<Integer> posteIds, String directionId) {
        if (posteIds == null || posteIds.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins un poste.");
        }
        Service direction = lireDirection(directionId);
        List<Poste> postes = posteRepo.findAllById(posteIds);
        if (postes.size() != new java.util.HashSet<>(posteIds).size()) {
            throw new IllegalArgumentException("Un des postes sélectionnés est introuvable.");
        }
        postes.forEach(p -> p.setDirection(direction));
        posteRepo.saveAll(postes);
        return postes.size();
    }

    public Map<Integer, Long> nombrePostesParDirection() {
        Map<Integer, Long> m = new HashMap<>();
        for (Poste p : posteRepo.findAll()) {
            if (p.getDirection() != null) m.merge(p.getDirection().getId(), 1L, Long::sum);
        }
        return m;
    }

    // ── Cohérence utilisateurs / postes ──────────────────────────────────────

    /**
     * Utilisateurs dont la direction diffère de celle de leur poste (ex. un Agent Comptable
     * rattaché à la DRH alors que le poste relève de la Direction Finance). Seuls les postes
     * rattachés à une direction sont contrôlés.
     */
    public List<afg.achat.afgApprovAchat.model.utilisateur.Utilisateur> utilisateursIncoherents() {
        return utilisateurRepo.findIncoherentsPosteDirection();
    }

    /**
     * Donne aux utilisateurs la direction de leur poste.
     *
     * @return nombre d'utilisateurs corrigés
     */
    @Transactional
    public int alignerSurDirectionDuPoste(java.util.Collection<Integer> utilisateurIds) {
        if (utilisateurIds == null || utilisateurIds.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins un utilisateur.");
        }
        int nb = 0;
        for (var u : utilisateurRepo.findAllById(utilisateurIds)) {
            if (u.getPoste() != null && u.getPoste().getDirection() != null
                    && (u.getService() == null || u.getService().getId() != u.getPoste().getDirection().getId())) {
                u.setService(u.getPoste().getDirection());
                nb++;
            }
        }
        return nb;
    }

    /** Direction choisie dans un formulaire ; {@code null} si rien n'est choisi. */
    private Service lireDirection(String directionId) {
        if (directionId == null || directionId.isBlank()) return null;
        try {
            return serviceRepo.findById(Integer.parseInt(directionId.trim()))
                    .orElseThrow(() -> new IllegalArgumentException("Direction introuvable."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Direction invalide.");
        }
    }

    @Transactional
    public Poste supprimerPoste(int id) {
        Poste p = posteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Poste introuvable."));
        long nb = utilisateurRepo.countByPoste_Id(id);
        if (nb > 0) {
            throw new IllegalArgumentException("Le poste « " + p.getPoste().trim() + " » est attribué à " + nb
                    + " utilisateur(s) : il ne peut pas être supprimé.");
        }
        posteRepo.delete(p);
        posteRepo.flush();
        return p;
    }

    private void verifierPosteLibre(String libelle, Integer idExclu) {
        for (Poste autre : posteRepo.findAll()) {
            if (idExclu != null && autre.getId() == idExclu) continue;
            if (cle(autre.getPoste()).equals(cle(libelle))) {
                throw new IllegalArgumentException("Le poste « " + autre.getPoste().trim() + " » existe déjà.");
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Map<Integer, Long> compter(List<Object[]> lignes) {
        Map<Integer, Long> m = new HashMap<>();
        for (Object[] r : lignes) m.put((Integer) r[0], (Long) r[1]);
        return m;
    }

    private static String libelleValide(String libelle, String quoi) {
        String l = libelle == null ? "" : libelle.trim().replaceAll("\\s+", " ");
        if (l.isEmpty()) throw new IllegalArgumentException(quoi + " est obligatoire.");
        if (l.length() > 255) throw new IllegalArgumentException(quoi + " ne doit pas dépasser 255 caractères.");
        return l;
    }

    /** Clé de comparaison : sans accents, casse, apostrophes typographiques ni espaces en trop. */
    private static String cle(String texte) {
        if (texte == null) return "";
        return Normalizer.normalize(texte.replace('’', '\''), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
