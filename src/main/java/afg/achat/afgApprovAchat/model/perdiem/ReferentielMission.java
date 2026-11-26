package afg.achat.afgApprovAchat.model.perdiem;

/** Élément d'un référentiel des missions (catégorie de personnel, zone) : partage l'écran de paramétrage. */
public interface ReferentielMission {

    Integer getId();

    String getCode();

    String getLibelle();

    Integer getOrdre();

    boolean estActif();
}
