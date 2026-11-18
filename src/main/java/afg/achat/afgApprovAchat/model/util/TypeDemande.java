package afg.achat.afgApprovAchat.model.util;

/**
 * Type d'une demande : il détermine le circuit de validation qu'elle suit.
 *
 * <p>Chaque type indique l'étape dont l'approbation clôt la demande. Les étapes
 * situées après dans le circuit standard ne sont pas parcourues.</p>
 *
 * <ul>
 *   <li>FOURNITURE : la demande de matériel, seule demande que l'on peut encore créer.
 *       Elle porte sur des articles actifs, suivis en stock, et s'arrête au contrôleur
 *       de gestion (ni D.F.C. ni S.G.).</li>
 *   <li>STANDARD : les anciennes demandes hors matériel (remboursement, sponsoring, per diem…).
 *       Elles ne se créent plus mais restent consultables et terminent leur circuit
 *       complet, jusqu'au S.G.</li>
 * </ul>
 *
 * <p>Les noms des constantes sont enregistrés en base : ne pas les renommer.</p>
 */
public enum TypeDemande {

    FOURNITURE("Matériel",       "Demandes de matériel", EtapeWorkflow.VALIDATION_N2, true,  true),
    STANDARD  ("Autre (ancien)", "Anciennes demandes",   EtapeWorkflow.VALIDATION_N4, false, false);

    private final String libelle;
    private final String libelleListe;
    private final EtapeWorkflow derniereValidation;
    private final boolean articlesEnStock;
    private final boolean creable;

    TypeDemande(String libelle, String libelleListe, EtapeWorkflow derniereValidation,
                boolean articlesEnStock, boolean creable) {
        this.libelle = libelle;
        this.libelleListe = libelleListe;
        this.derniereValidation = derniereValidation;
        this.articlesEnStock = articlesEnStock;
        this.creable = creable;
    }

    public String getLibelle() { return libelle; }

    /** Titre de la liste et du menu de ce type de demande. */
    public String getLibelleListe() { return libelleListe; }

    /** Les demandes de ce type portent sur des articles suivis en stock (bon de sortie possible). */
    public boolean isArticlesEnStock() { return articlesEnStock; }

    /** On peut encore créer des demandes de ce type. */
    public boolean isCreable() { return creable; }

    /** Étape dont l'approbation fait passer la demande à VALIDE. */
    public EtapeWorkflow getDerniereValidation() { return derniereValidation; }

    /** Les demandes créées avant l'ajout du type n'en ont pas : elles suivent le circuit standard. */
    public static TypeDemande ouStandard(TypeDemande type) {
        return type != null ? type : STANDARD;
    }

    /**
     * Lit un type saisi dans un formulaire.
     *
     * @return {@code null} si la valeur est vide
     * @throws IllegalArgumentException si la valeur ne correspond à aucun type
     */
    public static TypeDemande parse(String valeur) {
        if (valeur == null || valeur.isBlank()) return null;
        return valueOf(valeur.trim().toUpperCase());
    }
}
