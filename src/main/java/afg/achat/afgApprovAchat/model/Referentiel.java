package afg.achat.afgApprovAchat.model;

/**
 * Schéma PostgreSQL du référentiel commun aux applis achat et process : utilisateurs, postes,
 * directions ({@code service}), photos, valideurs et demandes de compte.
 * L'appli achat est la seule à y écrire ; voir docs/plan-migration-referentiel.md.
 */
public final class Referentiel {

    public static final String SCHEMA = "referentiel";

    private Referentiel() {
    }
}
