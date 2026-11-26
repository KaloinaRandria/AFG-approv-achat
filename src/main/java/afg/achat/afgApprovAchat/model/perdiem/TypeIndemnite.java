package afg.achat.afgApprovAchat.model.perdiem;

/**
 * Type d'indemnité d'un taux du barème, avec l'unité sur laquelle il se compte.
 *
 * <p>Une mission du 25 au 27 compte 3 jours et 2 nuitées : les repas se comptent par jour,
 * l'hébergement par nuitée.</p>
 *
 * <p>Les noms des constantes sont enregistrés en base : ne pas les renommer.</p>
 */
public enum TypeIndemnite {

    REPAS              ("Repas",              Unite.JOUR),
    HEBERGEMENT        ("Hébergement",        Unite.NUITEE),
    FORFAIT_JOURNALIER ("Forfait journalier", Unite.JOUR),
    DEPLACEMENT_LOCAL  ("Déplacement local",  Unite.JOUR);

    public enum Unite {
        JOUR("jour", "jours"),
        NUITEE("nuitée", "nuitées");

        private final String singulier;
        private final String pluriel;

        Unite(String singulier, String pluriel) {
            this.singulier = singulier;
            this.pluriel = pluriel;
        }

        public String getSingulier() { return singulier; }
        public String getPluriel()   { return pluriel; }
    }

    private final String libelle;
    private final Unite unite;

    TypeIndemnite(String libelle, Unite unite) {
        this.libelle = libelle;
        this.unite = unite;
    }

    public String getLibelle() { return libelle; }
    public Unite getUnite()    { return unite; }

    /**
     * Lit un type saisi dans un formulaire.
     *
     * @throws IllegalArgumentException si la valeur est vide ou inconnue
     */
    public static TypeIndemnite parse(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le type d'indemnité est obligatoire.");
        }
        try {
            return valueOf(valeur.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Type d'indemnité inconnu : " + valeur);
        }
    }
}
