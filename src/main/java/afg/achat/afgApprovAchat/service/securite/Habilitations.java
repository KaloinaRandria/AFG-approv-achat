package afg.achat.afgApprovAchat.service.securite;

import afg.achat.afgApprovAchat.model.utilisateur.Role;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import org.springframework.security.core.Authentication;

/**
 * Habilitations « créateur / valideur » par domaine, sur une convention de nommage unique :
 * <ul>
 *   <li>{@code <DOMAINE>_MAKER} : saisit et soumet (bon de commande manuel, compte utilisateur…) ;</li>
 *   <li>{@code <DOMAINE>_CHECKER} : valide ou rejette ce qu'un autre a soumis.</li>
 * </ul>
 * Domaines actuels : {@link #MG} (Moyens Généraux : bons de commande) et {@link #IT}
 * (Informatique : comptes utilisateurs). Un nouveau domaine ne demande que ses deux rôles en base.
 * L'administrateur a toujours les deux habilitations.
 *
 * <p>Utilisable dans une annotation : {@code @PreAuthorize("T(afg.achat.afgApprovAchat.service.securite.Habilitations).peutCreer(authentication, 'IT')")}.</p>
 */
public final class Habilitations {

    /** Moyens Généraux : MG_MAKER, MG_CHECKER. */
    public static final String MG = "MG";
    /** Informatique : IT_MAKER, IT_CHECKER (gestion des utilisateurs). */
    public static final String IT = "IT";

    public static final String ADMIN = "ADMIN";

    private Habilitations() {}

    // ── Noms des rôles ───────────────────────────────────────────────────────

    /** Nom du rôle en base (sans préfixe) : {@code IT_MAKER}. */
    public static String roleMaker(String domaine) {
        return domaine + "_MAKER";
    }

    /** Nom du rôle en base (sans préfixe) : {@code IT_CHECKER}. */
    public static String roleChecker(String domaine) {
        return domaine + "_CHECKER";
    }

    // ── Contrôles ────────────────────────────────────────────────────────────

    /** L'utilisateur connecté a le rôle (nom en base, sans le préfixe ROLE_). */
    public static boolean aRole(Authentication auth, String role) {
        if (auth == null || role == null) return false;
        String autorite = "ROLE_" + role;
        return auth.getAuthorities().stream().anyMatch(a -> autorite.equals(a.getAuthority()));
    }

    public static boolean estAdmin(Authentication auth) {
        return aRole(auth, ADMIN);
    }

    /**
     * Saisir / soumettre dans le domaine : administrateur, {@code <DOMAINE>_MAKER}
     * ou l'un des rôles équivalents propres au domaine (ex. MOYENS_GENERAUX pour MG).
     */
    public static boolean peutCreer(Authentication auth, String domaine, String... rolesEquivalents) {
        return estAdmin(auth) || aRole(auth, roleMaker(domaine)) || aUnDe(auth, rolesEquivalents);
    }

    /** Valider / rejeter dans le domaine : administrateur, {@code <DOMAINE>_CHECKER} ou rôle équivalent. */
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
