package afg.achat.afgApprovAchat.service.securite;

import afg.achat.afgApprovAchat.model.utilisateur.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regroupement des rôles par type, pour les présenter par blocs dans les formulaires utilisateur.
 * Un rôle inconnu de ce classement tombe dans « Autres ».
 */
public record GroupeRoles(String libelle, String aide, String icone, List<Role> roles) {

    private record Type(String libelle, String aide, String icone, Set<String> roles) {}

    /** Types de rôles, dans l'ordre d'affichage. */
    private static final List<Type> TYPES = List.of(
            new Type("Accès", "Connexion à l'application, saisie de ses propres demandes.",
                    "fa-user", Set.of("USER")),
            new Type("Service", "Domaine métier de la personne.",
                    "fa-building", Set.of("MOYENS_GENERAUX", "IT", "FINANCE", "FINANCE_CONSULT", Habilitations.RH)),
            new Type("Circuit de validation", "Étape tenue dans le circuit des demandes d'achat.",
                    "fa-route", Set.of("CONTROLEUR", "DFC", "SG")),
            new Type("Maker / checker", "Saisit (MAKER) ou valide (CHECKER) dans son service.",
                    "fa-user-check", Set.of(Habilitations.MAKER, Habilitations.CHECKER)),
            new Type("Administration et contrôle", "Accès étendu à toute l'application.",
                    "fa-user-shield", Set.of(Habilitations.ADMIN, "AUDIT"))
    );

    public static List<GroupeRoles> grouper(List<Role> roles) {
        Map<Type, List<Role>> parType = new LinkedHashMap<>();
        TYPES.forEach(t -> parType.put(t, new ArrayList<>()));
        List<Role> autres = new ArrayList<>();
        for (Role r : roles) {
            String nom = r.getRole() == null ? "" : r.getRole().trim();
            TYPES.stream().filter(t -> t.roles().contains(nom)).findFirst()
                    .ifPresentOrElse(t -> parType.get(t).add(r), () -> autres.add(r));
        }

        Comparator<Role> parNom = Comparator.comparing(r -> r.getRole() == null ? "" : r.getRole());
        List<GroupeRoles> groupes = new ArrayList<>();
        parType.forEach((t, rs) -> {
            if (!rs.isEmpty()) {
                rs.sort(parNom);
                groupes.add(new GroupeRoles(t.libelle(), t.aide(), t.icone(), rs));
            }
        });
        if (!autres.isEmpty()) {
            autres.sort(parNom);
            groupes.add(new GroupeRoles("Autres", null, "fa-tag", autres));
        }
        return groupes;
    }
}
