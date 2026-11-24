package afg.achat.afgApprovAchat.service.fournisseur;

import afg.achat.afgApprovAchat.exception.FournisseurAlreadyExistsException;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.util.FournisseurHistorique;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.fournisseur.FournisseurRepo;
import afg.achat.afgApprovAchat.service.util.FournisseurHistoriqueService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class FournisseurService {
    @Autowired
    FournisseurRepo fournisseurRepo;
    @Autowired
    FournisseurHistoriqueService fournisseurHistoriqueService;

    @PersistenceContext
    private EntityManager entityManager;

    public Fournisseur[] getAllFournisseurs() {
        return fournisseurRepo.findAll().toArray(new Fournisseur[0]);
    }

    /** Liste paginée des fournisseurs ; filtres vides ou {@code null} ignorés. */
    public Page<Fournisseur> rechercher(String nom, String mail, String contact, Pageable pageable) {
        String n = texte(nom);
        String m = motif(mail);
        String c = motif(contact);

        SearchResult<Fournisseur> resultat = Search.session((Session) entityManager)
                .search(Fournisseur.class)
                .where(f -> f.bool(b -> {
                    if (n.isEmpty() && m.isEmpty() && c.isEmpty()) {
                        b.must(f.matchAll());
                        return;
                    }
                    // Nom : mot proche (1 faute tolérée), ou morceau du nom ou de l'acronyme
                    if (!n.isEmpty()) {
                        String morceau = "*" + motif(n) + "*";
                        b.must(f.bool(nomOuAcronyme -> nomOuAcronyme
                                .should(f.match().field("nom").matching(n).fuzzy(1))
                                .should(f.wildcard().field("nom_kw").matching(morceau))
                                .should(f.wildcard().field("acronyme_kw").matching(morceau))));
                    }
                    if (!m.isEmpty()) {
                        b.must(f.wildcard().field("mail_kw").matching("*" + m + "*"));
                    }
                    if (!c.isEmpty()) {
                        b.must(f.wildcard().field("contact_kw").matching("*" + c + "*"));
                    }
                }))
                // Pertinence d'abord, puis ordre alphabétique (seul critère sans recherche)
                .sort(s -> s.score().then().field("nom_kw"))
                .fetch((int) pageable.getOffset(), pageable.getPageSize());

        return new PageImpl<>(resultat.hits(), pageable, resultat.total().hitCount());
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur.trim();
    }

    /**
     * Saisie prête pour une recherche joker sur un champ mot-clé : minuscules et sans accents
     * (comme le normaliseur « lowercase »), sans les caractères jokers de l'utilisateur.
     */
    private static String motif(String valeur) {
        String t = Normalizer.normalize(texte(valeur), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return t.replace("*", "").replace("?", "");
    }

    public Optional<Fournisseur> getFournisseurById(int id) {
        return fournisseurRepo.findById(id);
    }

    public Fournisseur getById(int id) {
        return fournisseurRepo.findById(id).orElse(null);
    }

    public void saveFournisseurIfNotExists(Fournisseur fournisseur) {

        if (fournisseurRepo.existsFournisseurByNomIgnoreCase(fournisseur.getNom())) {
            throw new FournisseurAlreadyExistsException(fournisseur.getNom());
        }

        fournisseurRepo.save(fournisseur);
    }

    private String norm(String s) {
        return s == null ? null : s.trim();
    }

    private boolean changed(String oldVal, String newVal) {
        // Compare en gérant null
        return !Objects.equals(oldVal, newVal);
    }


    @Transactional
    public Fournisseur modifierFournisseur(int id,
                                           String nom,
                                           String acronyme,
                                           String mail,
                                           String contact,
                                           String description) {

        // 1) Récupération du fournisseur existant
        Fournisseur fournisseur = this.getFournisseurById(id)
                .orElseThrow(() -> new RuntimeException("Fournisseur non trouvé"));

        // 2) Utilisateur courant
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String utilisateur = "Admin";
        if (principal instanceof Utilisateur user) {
            utilisateur = user.getNom() + " " + user.getPrenom();
        }

        List<FournisseurHistorique> historiques = new ArrayList<>();

        // 3) Normalisation des nouvelles valeurs
        String newNom         = norm(nom);
        String newAcronyme    = norm(acronyme);
        String newMail        = norm(mail);
        String newContact     = norm(contact);
        String newDescription = norm(description);

        // 4) Comparaison + historique + mise à jour

        // Nom
        if (newNom != null && changed(fournisseur.getNom(), newNom)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Nom",
                    fournisseur.getNom(),
                    newNom,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setNom(newNom);
        }

        // Acronyme
        if (newAcronyme != null && changed(fournisseur.getAcronyme(), newAcronyme)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Acronyme",
                    fournisseur.getAcronyme(),
                    newAcronyme,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setAcronyme(newAcronyme);
        }

        // Mail
        if (newMail != null && changed(fournisseur.getMail(), newMail)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Mail",
                    fournisseur.getMail(),
                    newMail,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setMail(newMail);
        }

        // Contact
        if (newContact != null && changed(fournisseur.getContact(), newContact)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Contact",
                    fournisseur.getContact(),
                    newContact,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setContact(newContact);
        }

        // Description (peut être null)
        if (changed(fournisseur.getDescription(), newDescription)) {
            historiques.add(new FournisseurHistorique(
                    fournisseur,
                    "Description",
                    fournisseur.getDescription(),
                    newDescription,
                    utilisateur,
                    String.valueOf(fournisseur.getId())
            ));
            fournisseur.setDescription(newDescription);
        }

        // 5) Sauvegarde uniquement s'il y a des modifications
        if (!historiques.isEmpty()) {
            fournisseur = fournisseurRepo.save(fournisseur);
            for (FournisseurHistorique h : historiques) {
                fournisseurHistoriqueService.saveFournisseurHistorique(h);
            }
        }

        return fournisseur;
    }


}
