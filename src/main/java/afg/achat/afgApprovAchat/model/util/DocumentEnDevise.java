package afg.achat.afgApprovAchat.model.util;

import java.time.LocalDateTime;

/**
 * Document dont les montants sont exprimés dans une devise (demande, bon de commande).
 *
 * <p>La devise et son cours en ariary sont copiés sur le document quand la devise est choisie,
 * puis figés quand le document n'est plus modifiable : un ancien document garde ainsi le taux
 * de son époque, même si le cours de la devise change ensuite.</p>
 *
 * <p>Les documents antérieurs à la devise n'en ont pas : ils sont en ariary (taux 1).</p>
 */
public interface DocumentEnDevise {

    Devise getDevise();

    void setDevise(Devise devise);

    /** Cours de la devise en ariary au moment où elle a été choisie (1 pour l'ariary). */
    Double getTauxChange();

    void setTauxChange(Double tauxChange);

    LocalDateTime getDateTaux();

    void setDateTaux(LocalDateTime dateTaux);

    /** Code ISO 4217 de la devise ; l'ariary si aucune n'est enregistrée. */
    default String getCodeDevise() {
        Devise d = getDevise();
        return d != null && d.getAcronyme() != null ? d.getAcronyme() : Devise.CODE_REFERENCE;
    }

    /** Taux utilisé pour les conversions : 1 si aucun taux valable n'est enregistré. */
    default double getTauxEffectif() {
        Double t = getTauxChange();
        return t == null || t <= 0 ? 1 : t;
    }

    /** Le document n'est pas en ariary. */
    default boolean isDeviseEtrangere() {
        return !Devise.CODE_REFERENCE.equalsIgnoreCase(getCodeDevise());
    }

    /** Contre-valeur en ariary d'un montant exprimé dans la devise du document. */
    default double enAriary(double montant) {
        return montant * getTauxEffectif();
    }

    /** Montant en ariary exprimé dans la devise du document. */
    default double depuisAriary(double montantAriary) {
        return montantAriary / getTauxEffectif();
    }

    /** Choisit la devise et copie son cours actuel. {@code null} = ariary. */
    default void appliquerDevise(Devise devise, LocalDateTime maintenant) {
        setDevise(devise);
        setTauxChange(devise == null || devise.getCoursAriary() <= 0 ? 1.0 : devise.getCoursAriary());
        setDateTaux(maintenant);
    }
}
