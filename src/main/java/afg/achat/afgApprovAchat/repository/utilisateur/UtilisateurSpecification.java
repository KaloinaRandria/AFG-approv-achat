package afg.achat.afgApprovAchat.repository.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Spécifications pour les recherches dynamiques d'utilisateurs
 * Approche scalable et maintenable pour les filtres complexes
 */
public class UtilisateurSpecification {

    private UtilisateurSpecification() {
        throw new AssertionError("Classe utility, pas d'instanciation");
    }

    /**
     * Filtre par nom (case-insensitive, partial match)
     */
    public static Specification<Utilisateur> nomLike(String nom) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(nom)) {
                return null; // Ignore si vide
            }
            return cb.like(cb.lower(root.get("nom")), "%" + nom.toLowerCase() + "%");
        };
    }

    /**
     * Filtre par prénom (case-insensitive, partial match)
     */
    public static Specification<Utilisateur> prenomLike(String prenom) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(prenom)) {
                return null;
            }
            return cb.like(cb.lower(root.get("prenom")), "%" + prenom.toLowerCase() + "%");
        };
    }

    /**
     * Filtre par email (case-insensitive, partial match)
     */
    public static Specification<Utilisateur> mailLike(String mail) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(mail)) {
                return null;
            }
            return cb.like(cb.lower(root.get("mail")), "%" + mail.toLowerCase() + "%");
        };
    }

    /**
     * Filtre par service (case-insensitive, partial match)
     * Utilise le LEFT JOIN correctement avec JPA Criteria API
     */
    public static Specification<Utilisateur> serviceLibelleLike(String serviceLibelle) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(serviceLibelle)) {
                return null;
            }
            return cb.like(
                    cb.lower(root.join("service", JoinType.LEFT).get("libelle")),
                    "%" + serviceLibelle.toLowerCase() + "%"
            );
        };
    }

    /**
     * Recherche textuelle sur nom, prénom ou email
     */
    public static Specification<Utilisateur> searchTerm(String searchTerm) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(searchTerm)) {
                return null;
            }
            String lowerSearchTerm = "%" + searchTerm.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("nom")), lowerSearchTerm),
                    cb.like(cb.lower(root.get("prenom")), lowerSearchTerm),
                    cb.like(cb.lower(root.get("mail")), lowerSearchTerm)
            );
        };
    }

    /**
     * Combine tous les filtres (nom, prénom, email, service) avec AND
     */
    public static Specification<Utilisateur> filterBy(String nom, String prenom, String mail, String service) {
        return Specification.where(nomLike(nom))
                .and(prenomLike(prenom))
                .and(mailLike(mail))
                .and(serviceLibelleLike(service));
    }
}

