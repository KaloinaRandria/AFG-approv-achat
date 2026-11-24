package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import afg.achat.afgApprovAchat.repository.FamilleRepo;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.work.SearchIndexingPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class FamilleService {
    @Autowired
    FamilleRepo familleRepo;

    @Autowired
    ArticleRepo articleRepo;

    @Autowired
    EntityManager entityManager;

    @Autowired
    afg.achat.afgApprovAchat.repository.util.ArticleHistoriqueRepo articleHistoriqueRepo;

    public Famille[] getAllFamilles() {
        return familleRepo.findAll().toArray(new Famille[0]);
    }

    public Optional<Famille> getFamilleById(int id) {
        return familleRepo.findById(id);
    }

    public Famille getFamilleByDesc(String desc) {
        if (desc == null) return null;
        return familleRepo.findByDescriptionIgnoreCaseTrim(desc);
    }

    public void saveFamille(Famille famille) {
        familleRepo.save(famille);
    }


        public void saveFamilleIfNotExists(String description) {
            if (!familleRepo.existsFamilleByDescriptionIgnoreCase(description)) {
                Famille famille = new Famille();
                famille.setDescription(description.trim());
                familleRepo.save(famille);
            }
        }

    // ── Paramétrage des familles (Moyens Généraux, administrateur) ──────────

    /** Familles triées par libellé, sans tenir compte des accents ni de la casse. */
    public List<Famille> getFamillesTriees() {
        return familleRepo.findAll().stream()
                .sorted(Comparator.comparing(f -> cle(f.getDescription())))
                .toList();
    }

    /** Nombre d'articles par famille (clé : identifiant de la famille). */
    public Map<Integer, Long> nombreArticlesParFamille() {
        Map<Integer, Long> m = new HashMap<>();
        for (Object[] r : articleRepo.compterParFamille()) {
            m.put((Integer) r[0], (Long) r[1]);
        }
        return m;
    }

    /** @throws IllegalArgumentException si le libellé est vide ou existe déjà (accents et casse ignorés) */
    @Transactional
    public Famille creer(String description) {
        String d = libelleValide(description);
        verifierUnicite(d, null);
        Famille f = new Famille();
        f.setDescription(d);
        return familleRepo.save(f);
    }

    /**
     * Renomme la famille. Les articles de la famille sont réindexés : la recherche
     * par famille de la liste des articles passe par l'index Lucene.
     */
    @Transactional
    public Famille renommer(int id, String description) {
        Famille f = lire(id);
        String d = libelleValide(description);
        verifierUnicite(d, id);
        f.setDescription(d);
        familleRepo.save(f);

        SearchIndexingPlan plan = Search.session((Session) entityManager).indexingPlan();
        for (Article a : articleRepo.findByFamille(f)) {
            plan.addOrUpdate(a);
        }
        return f;
    }

    /** Suppression réservée à une famille sans article. */
    @Transactional
    public Famille supprimer(int id) {
        Famille f = lire(id);
        long nb = articleRepo.countByFamille(f);
        if (nb > 0) {
            throw new IllegalArgumentException("La famille « " + f.getDescription() + " » contient " + nb
                    + " article(s) : elle ne peut pas être supprimée.");
        }
        familleRepo.delete(f);
        familleRepo.flush(); // une référence restante échoue ici (DataIntegrityViolationException), pas au commit
        return f;
    }

    /**
     * Fusionne deux familles : les articles de la famille source passent dans la famille cible
     * (changement tracé dans l'historique de chaque article), puis la source, vide, est supprimée.
     *
     * @param auteur nom affiché dans l'historique des articles
     * @return nombre d'articles déplacés
     */
    @Transactional
    public int fusionner(int sourceId, int cibleId, String auteur) {
        if (sourceId == cibleId) {
            throw new IllegalArgumentException("Choisissez une famille cible différente de la famille fusionnée.");
        }
        return deplacerEtSupprimer(lire(sourceId), lire(cibleId), auteur);
    }

    /**
     * Fusionne plusieurs familles dans une famille conservée, en une seule transaction :
     * si l'une échoue, aucune n'est modifiée.
     *
     * @param sourceIds familles à fusionner (la cible, si elle y figure, est ignorée)
     * @return nombre total d'articles déplacés
     */
    @Transactional
    public int fusionnerPlusieurs(java.util.Collection<Integer> sourceIds, int cibleId, String auteur) {
        Famille cible = lire(cibleId);
        List<Integer> sources = sourceIds == null ? List.of()
                : sourceIds.stream().filter(java.util.Objects::nonNull).distinct().filter(id -> id != cibleId).toList();
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins une famille à fusionner en plus de la famille conservée.");
        }
        int total = 0;
        for (Integer id : sources) {
            total += deplacerEtSupprimer(lire(id), cible, auteur);
        }
        return total;
    }

    private int deplacerEtSupprimer(Famille source, Famille cible, String auteur) {

        List<Article> articles = articleRepo.findByFamille(source);
        for (Article a : articles) {
            a.setFamille(cible); // entité suivie : enregistrée et réindexée au commit
            articleHistoriqueRepo.save(new afg.achat.afgApprovAchat.model.util.ArticleHistorique(
                    a, "Famille", source.getDescription(), cible.getDescription(),
                    auteur + " (fusion de familles)", a.getCodeArticle()));
        }
        articleRepo.flush();

        familleRepo.delete(source);
        familleRepo.flush(); // une référence restante échoue ici, et annule toute la fusion
        return articles.size();
    }

    // ── Doublons probables ───────────────────────────────────────────────────

    private static final java.util.Set<String> MOTS_VIDES = java.util.Set.of(
            "de", "du", "des", "d", "la", "le", "les", "l", "et", "a", "au", "aux", "en", "pour");

    /**
     * Groupes de familles qui désignent probablement la même chose, à fusionner.
     *
     * <p>Deux familles sont rapprochées si leurs libellés sont identiques une fois ignorés
     * les accents, la casse, la ponctuation, les petits mots (de, du, des…) et le pluriel
     * (« Fourniture de banque » / « Fournitures de banque », « Entretien vehicule » /
     * « Entretien véhicule »), ou s'ils ne diffèrent que d'une ou deux lettres (faute de frappe).</p>
     *
     * <p>Chaque groupe est trié par nombre d'articles décroissant : la première famille
     * est celle qu'il est naturel de conserver.</p>
     */
    public List<List<Famille>> doublonsProbables() {
        List<Famille> familles = getFamillesTriees();
        Map<Integer, Long> nbArticles = nombreArticlesParFamille();
        int n = familles.size();
        String[] cles = familles.stream().map(f -> cleRapprochement(f.getDescription())).toArray(String[]::new);

        // Union-find : les rapprochements s'enchaînent (A~B et B~C → un seul groupe)
        int[] parent = java.util.stream.IntStream.range(0, n).toArray();
        java.util.function.IntUnaryOperator racine = new java.util.function.IntUnaryOperator() {
            public int applyAsInt(int i) {
                while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
                return i;
            }
        };
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (cles[i].isEmpty() || cles[j].isEmpty()) continue;
                if (procheLibelle(cles[i], cles[j])) {
                    parent[racine.applyAsInt(i)] = racine.applyAsInt(j);
                }
            }
        }

        Map<Integer, List<Famille>> groupes = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groupes.computeIfAbsent(racine.applyAsInt(i), k -> new java.util.ArrayList<>()).add(familles.get(i));
        }
        Comparator<Famille> plusDArticles = Comparator.comparingLong(
                (Famille f) -> nbArticles.getOrDefault(f.getId(), 0L)).reversed();
        return groupes.values().stream()
                .filter(g -> g.size() > 1)
                .map(g -> g.stream().sorted(plusDArticles).toList())
                .toList();
    }

    /** Libellé réduit : sans accents, casse, ponctuation, petits mots ni pluriel. */
    static String cleRapprochement(String libelle) {
        return Arrays.stream(cle(libelle).split("[^a-z0-9]+"))
                .filter(m -> !m.isEmpty() && !MOTS_VIDES.contains(m))
                .map(m -> m.length() > 3 && (m.endsWith("s") || m.endsWith("x")) ? m.substring(0, m.length() - 1) : m)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /** Même clé, ou une à deux lettres d'écart sur un libellé assez long pour que ce soit une faute de frappe. */
    private static boolean procheLibelle(String a, String b) {
        if (a.equals(b)) return true;
        int min = Math.min(a.length(), b.length());
        int toleree = min >= 12 ? 2 : (min >= 6 ? 1 : 0);
        return toleree > 0 && Math.abs(a.length() - b.length()) <= toleree && distance(a, b) <= toleree;
    }

    /** Distance de Levenshtein (insertions, suppressions, substitutions). */
    private static int distance(String a, String b) {
        int[] prec = new int[b.length() + 1];
        int[] cour = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prec[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cour[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cout = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cour[j] = Math.min(Math.min(cour[j - 1] + 1, prec[j] + 1), prec[j - 1] + cout);
            }
            int[] t = prec; prec = cour; cour = t;
        }
        return prec[b.length()];
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Famille lire(int id) {
        return familleRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Famille introuvable."));
    }

    private static String libelleValide(String description) {
        String d = description == null ? "" : description.trim().replaceAll("\\s+", " ");
        if (d.isEmpty()) {
            throw new IllegalArgumentException("Le libellé de la famille est obligatoire.");
        }
        if (d.length() > 255) {
            throw new IllegalArgumentException("Le libellé ne doit pas dépasser 255 caractères.");
        }
        return d;
    }

    /** « Entretien vehicule » et « Entretien véhicule » sont la même famille. */
    private void verifierUnicite(String description, Integer idExclu) {
        String cle = cle(description);
        Arrays.stream(getAllFamilles())
                .filter(f -> idExclu == null || f.getId() != idExclu)
                .filter(f -> cle(f.getDescription()).equals(cle))
                .findFirst()
                .ifPresent(f -> {
                    throw new IllegalArgumentException("La famille « " + f.getDescription() + " » existe déjà.");
                });
    }

    private static String cle(String texte) {
        if (texte == null) return "";
        return Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
