package afg.achat.afgApprovAchat.service.securite;

import afg.achat.afgApprovAchat.model.utilisateur.Role;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import org.springframework.security.core.Authentication;

/**
 * Habilitations « créateur / valideur », avec deux rôles génériques valables pour tous les domaines :
 * <ul>
 *   <li>{@link #MAKER} : saisit et soumet (bon de commande manuel, compte utilisateur…) ;</li>
 *   <li>{@link #CHECKER} : valide ou rejette ce qu'un autre a soumis.</li>
 * </ul>
 * Le domaine est donné par le rôle métier de l'utilisateur : {@link #MG} (MOYENS_GENERAUX : bons de
 * commande, CODEP, transmission finance) ou {@link #IT} (comptes utilisateurs). Un utilisateur
 * IT + CHECKER valide les comptes, un utilisateur MOYENS_GENERAUX + CHECKER valide les bons de commande.
 * Un nouveau domaine n'a besoin d'aucun rôle supplémentaire. L'administrateur a toujours les deux habilitations.
 *
 * <p>Un rôle requis peut être composé : {@code "MOYENS_GENERAUX+CHECKER"} exige les deux rôles
 * (voir {@link #aRole}).</p>
 *
 * <p>Utilisable dans une annotation : {@code @PreAuthorize("T(afg.achat.afgApprovAchat.service.securite.Habilitations).peutCreer(authentication, 'IT')")}.</p>
 */
public final class Habilitations {

    /** Rôle générique : saisit et soumet. */
    public static final String MAKER = "MAKER";
    /** Rôle générique : valide ou rejette. */
    public static final String CHECKER = "CHECKER";

    /** Domaine Moyens Généraux (rôle métier). */
    public static final String MG = "MOYENS_GENERAUX";
    /** Domaine Informatique (rôle métier) : gestion des utilisateurs. */
    public static final String IT = "IT";

    public static final String ADMIN = "ADMIN";

    /** Séparateur d'un rôle composé : tous les rôles sont exigés. */
    public static final String ET = "+";

    private Habilitations() {}

    // ── Noms des rôles ───────────────────────────────────────────────────────

    /** Rôle composé du créateur d'un domaine : {@code IT+MAKER}. */
    public static String roleMaker(String domaine) {
        return domaine + ET + MAKER;
    }

    /** Rôle composé du valideur d'un domaine : {@code IT+CHECKER}. */
    public static String roleChecker(String domaine) {
        return domaine + ET + CHECKER;
    }

    /** Libellé lisible d'un rôle, éventuellement composé : {@code IT + CHECKER}. */
    public static String libelle(String role) {
        return role == null ? "" : role.replace(ET, " " + ET + " ");
    }

    // ── Contrôles ────────────────────────────────────────────────────────────

    /**
     * L'utilisateur connecté a le rôle (nom en base, sans le préfixe ROLE_). Un rôle composé
     * ({@code MOYENS_GENERAUX+CHECKER}) exige chacun de ses rôles.
     */
    public static boolean aRole(Authentication auth, String role) {
        if (auth == null || role == null || role.isBlank()) return false;
        for (String r : role.split("\\" + ET)) {
            String autorite = "ROLE_" + r.trim();
            if (auth.getAuthorities().stream().noneMatch(a -> autorite.equals(a.getAuthority()))) {
                return false;
            }
        }
        return true;
    }

    public static boolean estAdmin(Authentication auth) {
        return aRole(auth, ADMIN);
    }

    /**
     * Saisir / soumettre dans le domaine : administrateur, rôle du domaine + {@link #MAKER},
     * ou l'un des rôles équivalents (ex. MOYENS_GENERAUX seul pour les bons de commande manuels).
     */
    public static boolean peutCreer(Authentication auth, String domaine, String... rolesEquivalents) {
        return estAdmin(auth) || aRole(auth, roleMaker(domaine)) || aUnDe(auth, rolesEquivalents);
    }

    /** Valider / rejeter dans le domaine : administrateur, rôle du domaine + {@link #CHECKER} ou rôle équivalent. */
    public static boolean peutValider(Authentication auth, String domaine, String... rolesEquivalents) {
        return estAdmin(auth) || aRole(auth, roleChecker(domaine)) || aUnDe(auth, rolesEquivalents);
    }

    /**
     * Principe des quatre yeux : une demande se valide par une autre personne que son auteur,
     * sauf pour l'administrateur.
     */
    public static boolean peutValiderDemandeDe(Authentication auth, String domaine,
                                               Utilisateur auteur, Utilisateur valideur) {
        if (!peutValider(auth, domaine)) return false;
        if (estAdmin(auth)) return true;
        return auteur == null || valideur == null || auteur.getId() != valideur.getId();
    }

    private static boolean aUnDe(Authentication auth, String... roles) {
        if (roles == null) return false;
        for (String r : roles) {
            if (aRole(auth, r)) return true;
        }
        return false;
    }

    // ── Utilisateurs (rôles en base) ─────────────────────────────────────────

    /** Le compte a le rôle en base, éventuellement composé (chacun de ses rôles). */
    public static boolean compteARole(Utilisateur u, String role) {
        if (u == null || u.getRoles() == null || role == null || role.isBlank()) return false;
        for (String r : role.split("\\" + ET)) {
            String nom = r.trim();
            if (u.getRoles().stream().noneMatch(x -> x.getRole() != null && nom.equals(x.getRole().trim()))) {
                return false;
            }
        }
        return true;
    }

    // ── Rôle ADMIN : réservé aux administrateurs ─────────────────────────────

    public static boolean estRoleAdmin(Role r) {
        if (r == null || r.getRole() == null) return false;
        String nom = r.getRole().trim();
        return ADMIN.equalsIgnoreCase(nom) || ("ROLE_" + ADMIN).equalsIgnoreCase(nom);
    }

    public static boolean estCompteAdmin(Utilisateur u) {
        return u != null && u.getRoles() != null && u.getRoles().stream().anyMatch(Habilitations::estRoleAdmin);
    }
}
