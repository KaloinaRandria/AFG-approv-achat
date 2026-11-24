package afg.achat.afgApprovAchat.model.util;

/**
 * Type d'une demande : il détermine le circuit de validation qu'elle suit.
 *
 * <p>Chaque type indique l'étape dont l'approbation clôt la demande. Les étapes
 * situées après dans le circuit standard ne sont pas parcourues.</p>
 *
 * <ul>
 *   <li>FOURNITURE : la demande de matériel. Elle porte sur des articles actifs, suivis
 *       en stock, et s'arrête au contrôleur de gestion (ni D.F.C. ni S.G.).</li>
 *   <li>AUTRE : une dépense hors matériel (sponsoring, remboursement, indemnité de repas,
 *       lot / récompense, droits divers / honoraires…). Une seule dépense par demande
 *       ({@code DemandeAutreDetail}), sans article ni bon de commande : paiement direct.
 *       Circuit complet, jusqu'au S.G.</li>
 *   <li>STANDARD : les anciennes demandes hors matériel, saisies avec des articles.
 *       Elles ne se créent plus mais restent consultables et terminent leur circuit
 *       complet, jusqu'au S.G.</li>
 * </ul>
 *
 * <p>Les noms des constantes sont enregistrés en base : ne pas les renommer.</p>
 */
public enum TypeDemande {

    FOURNITURE("Matériel",       "Demandes de matériel", EtapeWorkflow.VALIDATION_N2, true,  true,  ModeTraitementEnum.ATTENTE_AMG),
    AUTRE     ("Autre",          "Demandes autres",      EtapeWorkflow.VALIDATION_N4, false, true,  ModeTraitementEnum.PAIEMENT_DIRECT),
    STANDARD  ("Autre (ancien)", "Anciennes demandes",   EtapeWorkflow.VALIDATION_N4, false, false, null);

    private final String libelle;
    private final String libelleListe;
    private final EtapeWorkflow derniereValidation;
    private final boolean articlesEnStock;
    private final boolean creable;
    private final ModeTraitementEnum modeTraitement;

    TypeDemande(String libelle, String libelleListe, EtapeWorkflow derniereValidation,
                boolean articlesEnStock, boolean creable, ModeTraitementEnum modeTraitement) {
        this.libelle = libelle;
        this.libelleListe = libelleListe;
        this.derniereValidation = derniereValidation;
        this.articlesEnStock = articlesEnStock;
        this.creable = creable;
        this.modeTraitement = modeTraitement;
    }

    /**
     * Mode de traitement imposé par le type : matériel → en attente Moyens Généraux (bon de commande),
     * Autre → paiement direct (sans bon de commande). {@code null} pour les anciennes demandes,
     * dont le mode est encore choisi par les Moyens Généraux.
     */
    public ModeTraitementEnum getModeTraitement() { return modeTraitement; }

    public String getLibelle() { return libelle; }

    /** Titre de la liste et du menu de ce type de demande. */
    public String getLibelleListe() { return libelleListe; }

    /** Les demandes de ce type portent sur des articles suivis en stock (bon de sortie possible). */
    public boolean isArticlesEnStock() { return articlesEnStock; }

    /** On peut encore créer des demandes de ce type. */
    public boolean isCreable() { return creable; }

    /** Une seule dépense, sans article, réglée en paiement direct. */
    public boolean isDepenseAutre() { return this == AUTRE; }

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
