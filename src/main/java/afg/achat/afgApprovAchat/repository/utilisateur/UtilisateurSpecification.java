package afg.achat.afgApprovAchat.repository.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Recherche multicritère de la liste des utilisateurs.
 */
public final class UtilisateurSpecification {

    private UtilisateurSpecification() {}

    /**
     * @param recherche texte cherché dans le nom, le prénom, « prénom nom », « nom prénom » et l'email
     * @param serviceId service exact (null = tous)
     * @param roleId    rôle détenu (null = tous)
     * @param actif     statut du compte (null = tous)
     */
    public static Specification<Utilisateur> rechercher(String recherche, Integer serviceId,
                                                        Integer roleId, Boolean actif) {
        return (root, query, cb) -> {
            List<Predicate> predicats = new ArrayList<>();

            if (StringUtils.hasText(recherche)) {
                String motif = "%" + recherche.trim().toLowerCase() + "%";
                predicats.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("nom"), "")), motif),
                        cb.like(cb.lower(cb.coalesce(root.get("prenom"), "")), motif),
                        cb.like(cb.lower(cb.coalesce(root.get("mail"), "")), motif),
                        cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(root.get("prenom"), ""), " "),
                                cb.coalesce(root.get("nom"), ""))), motif),
                        cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(root.get("nom"), ""), " "),
                                cb.coalesce(root.get("prenom"), ""))), motif)
                ));
            }

            if (serviceId != null) {
                predicats.add(cb.equal(root.get("service").get("id"), serviceId));
            }

            if (roleId != null) {
                // Sous-requête : évite les doublons d'une jointure sur une collection
                Subquery<Integer> sq = query.subquery(Integer.class);
                var u = sq.from(Utilisateur.class);
                sq.select(u.get("id")).where(
                        cb.equal(u.get("id"), root.get("id")),
                        cb.equal(u.join("roles").get("id"), roleId));
                predicats.add(cb.exists(sq));
            }

            if (actif != null) {
                predicats.add(cb.equal(root.get("actif"), actif));
            }

            return cb.and(predicats.toArray(new Predicate[0]));
        };
    }
}
