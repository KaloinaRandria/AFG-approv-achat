package afg.achat.afgApprovAchat.model.util;

import lombok.Getter;
import lombok.Setter;

@Getter
public enum ModeTraitementEnum {
    PAIEMENT_DIRECT("Paiement direct", false),
    ATTENTE_AMG("En attente Moyens Généraux", true);
    private final String libelle;
    private final boolean necessiteBonCommande;

    ModeTraitementEnum(String libelle, boolean necessiteBonCommande) {
        this.libelle = libelle;
        this.necessiteBonCommande = necessiteBonCommande;
    }
}
