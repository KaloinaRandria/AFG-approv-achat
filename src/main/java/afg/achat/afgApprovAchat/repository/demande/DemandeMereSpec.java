package afg.achat.afgApprovAchat.repository.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DemandeMereSpec {

    private DemandeMereSpec() {}

    public static Specification<DemandeMere> build(SearchCriteria c) {
        return (root, query, cb) -> {

            // Éviter les doublons sur les joins (important pour COUNT)
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("demandeur", JoinType.LEFT);
            }
            Join<Object, Object> dmd = root.join("demandeur", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();

            // ── Plage de dates (toujours présente) ──────────────────────────
            predicates.add(cb.between(root.get("dateDemande"), c.dateFrom(), c.dateTo()));

            // ── Filtres texte ────────────────────────────────────────────────
            if (hasText(c.num()))
                predicates.add(likeCI(cb, root.get("id"), c.num()));

            if (hasText(c.demandeur())) {
                predicates.add(cb.or(
                        likeCI(cb, dmd.get("prenom"), c.demandeur()),
                        likeCI(cb, dmd.get("nom"),    c.demandeur())
                ));
            }

            if (hasText(c.type()))
                predicates.add(cb.equal(cb.lower(root.get("natureDemande").as(String.class)),
                        c.type().toLowerCase()));

            if (hasText(c.priorite()))
                predicates.add(cb.equal(cb.lower(root.get("priorite").as(String.class)),
                        c.priorite().toLowerCase()));

            if (hasText(c.motif()))
                predicates.add(likeCI(cb, root.get("motifEvoque"), c.motif()));

            // ── Statut(s) ────────────────────────────────────────────────────
            if (c.statuts() != null && !c.statuts().isEmpty()) {
                predicates.add(root.get("statut").in(c.statuts()));
            } else if (c.statut() != null) {
                predicates.add(cb.equal(root.get("statut"), c.statut()));
            }

            // ── Restriction sur les demandeurs visibles ──────────────────────
            if (c.demandeurIds() != null && !c.demandeurIds().isEmpty()) {
                predicates.add(dmd.get("id").in(c.demandeurIds()));
            }

            // ── Logique backoffice : statuts du rôle OU mes propres demandes ─
            if (c.roleStatuts() != null && !c.roleStatuts().isEmpty()
                    && c.myVisibleIds() != null && !c.myVisibleIds().isEmpty()) {

                Predicate byRole = root.get("statut").in(c.roleStatuts());
                Predicate myOwn = dmd.get("id").in(c.myVisibleIds());
                if (c.statut() != null) {
                    myOwn = cb.and(myOwn, cb.equal(root.get("statut"), c.statut()));
                }
                predicates.add(cb.or(byRole, myOwn));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static Predicate likeCI(CriteriaBuilder cb, Path<String> path, String value) {
        return cb.like(cb.lower(cb.coalesce(path, "")),
                "%" + value.toLowerCase() + "%");
    }

    // ── Criteria record ──────────────────────────────────────────────────────

    public record SearchCriteria(
            String          num,
            String          demandeur,
            String          type,
            String          priorite,
            String          motif,
            Integer         statut,
            List<Integer>   statuts,
            LocalDateTime   dateFrom,
            LocalDateTime   dateTo,
            List<Integer>   demandeurIds,
            List<Integer>   roleStatuts,
            Integer         myStatut,
            List<Integer>   myVisibleIds
    ) {
        // Builder statique pour éviter les constructeurs à 13 params
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String        num        = "";
            private String        demandeur  = "";
            private String        type       = "";
            private String        priorite   = "";
            private String        motif      = "";
            private Integer       statut     = null;
            private List<Integer> statuts    = null;
            private LocalDateTime dateFrom   = LocalDateTime.of(1900,1,1,0,0);
            private LocalDateTime dateTo     = LocalDateTime.of(2999,12,31,23,59,59);
            private List<Integer> demandeurIds  = null;
            private List<Integer> roleStatuts   = null;
            private Integer       myStatut      = null;
            private List<Integer> myVisibleIds  = null;

            public Builder num(String v)              { num = v == null ? "" : v.trim(); return this; }
            public Builder demandeur(String v)        { demandeur = v == null ? "" : v.trim(); return this; }
            public Builder type(String v)             { type = v == null ? "" : v.trim(); return this; }
            public Builder priorite(String v)         { priorite = v == null ? "" : v.trim(); return this; }
            public Builder motif(String v)            { motif = v == null ? "" : v.trim(); return this; }
            public Builder statut(Integer v)          { statut = (v == null || v == 0) ? null : v; return this; }
            public Builder statuts(List<Integer> v)   { statuts = (v == null || v.isEmpty()) ? null : v; return this; }
            public Builder dateFrom(LocalDateTime v)  { if (v != null) dateFrom = v; return this; }
            public Builder dateTo(LocalDateTime v)    { if (v != null) dateTo = v; return this; }
            public Builder demandeurIds(List<Integer> v)  { demandeurIds = v; return this; }
            public Builder roleStatuts(List<Integer> v)   { roleStatuts = v; return this; }
            public Builder myStatut(Integer v)            { myStatut = v; return this; }
            public Builder myVisibleIds(List<Integer> v)  { myVisibleIds = v; return this; }

            public SearchCriteria build() {
                return new SearchCriteria(num, demandeur, type, priorite, motif,
                        statut, statuts, dateFrom, dateTo,
                        demandeurIds, roleStatuts, myStatut, myVisibleIds);
            }
        }
    }
}