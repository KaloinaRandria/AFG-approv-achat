package afg.achat.afgApprovAchat.repository.paiement;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.paiement.PaiementDirect;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class PaiementDirectSpec {

    private PaiementDirectSpec() {}

    public static Specification<PaiementDirect> build(SearchCriteria c) {
        return (root, query, cb) -> {

            Join<PaiementDirect, DemandeMere> demande = root.join("demande", JoinType.INNER);

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                demande.fetch("demandeur", JoinType.LEFT);
            }
            Join<Object, Object> dmd = demande.join("demandeur", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();

            // Plage de dates
            predicates.add(cb.between(demande.get("dateDemande"), c.dateFrom(), c.dateTo()));

            // Filtres texte
            if (hasText(c.num()))
                predicates.add(likeCI(cb, demande.get("id"), c.num()));

            if (hasText(c.demandeur())) {
                predicates.add(cb.or(
                        likeCI(cb, dmd.get("prenom"), c.demandeur()),
                        likeCI(cb, dmd.get("nom"),    c.demandeur())
                ));
            }

            if (hasText(c.type()))
                predicates.add(cb.equal(cb.lower(demande.get("natureDemande").as(String.class)),
                        c.type().toLowerCase()));

            if (hasText(c.priorite()))
                predicates.add(cb.equal(cb.lower(demande.get("priorite").as(String.class)),
                        c.priorite().toLowerCase()));

            if (hasText(c.motif()))
                predicates.add(likeCI(cb, demande.get("motifEvoque"), c.motif()));

            // Statut de paiement direct
            if (c.statuts() != null && !c.statuts().isEmpty()) {
                predicates.add(root.get("statut").in(c.statuts()));
            } else if (c.statut() != null) {
                predicates.add(cb.equal(root.get("statut"), c.statut()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static Predicate likeCI(CriteriaBuilder cb, Path<String> path, String value) {
        return cb.like(cb.lower(cb.coalesce(path, "")), "%" + value.toLowerCase() + "%");
    }

    public record SearchCriteria(
            String                                    num,
            String                                    demandeur,
            String                                    type,
            String                                    priorite,
            String                                    motif,
            PaiementDirect.StatutPaiementDirect       statut,
            List<PaiementDirect.StatutPaiementDirect> statuts,
            LocalDateTime                             dateFrom,
            LocalDateTime                             dateTo
    ) {
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String                                    num       = "";
            private String                                    demandeur = "";
            private String                                    type      = "";
            private String                                    priorite  = "";
            private String                                    motif     = "";
            private PaiementDirect.StatutPaiementDirect       statut    = null;
            private List<PaiementDirect.StatutPaiementDirect> statuts   = null;
            private LocalDateTime                             dateFrom  = LocalDateTime.of(1900,1,1,0,0);
            private LocalDateTime                             dateTo    = LocalDateTime.of(2999,12,31,23,59,59);

            public Builder num(String v)        { num = v == null ? "" : v.trim(); return this; }
            public Builder demandeur(String v)  { demandeur = v == null ? "" : v.trim(); return this; }
            public Builder type(String v)       { type = v == null ? "" : v.trim(); return this; }
            public Builder priorite(String v)   { priorite = v == null ? "" : v.trim(); return this; }
            public Builder motif(String v)      { motif = v == null ? "" : v.trim(); return this; }
            public Builder statut(PaiementDirect.StatutPaiementDirect v) { statut = v; return this; }
            public Builder statuts(List<PaiementDirect.StatutPaiementDirect> v) { statuts = (v == null || v.isEmpty()) ? null : v; return this; }
            public Builder dateFrom(LocalDateTime v)  { if (v != null) dateFrom = v; return this; }
            public Builder dateTo(LocalDateTime v)    { if (v != null) dateTo = v; return this; }

            public SearchCriteria build() {
                return new SearchCriteria(num, demandeur, type, priorite, motif, statut, statuts, dateFrom, dateTo);
            }
        }
    }
}
