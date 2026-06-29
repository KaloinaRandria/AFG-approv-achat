package afg.achat.afgApprovAchat.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BonCommandeLigneDTO {
    private int demandeFilleId;
    private int articleId;
    private String designationFournisseur;
    private double quantiteCommandee;
    private double prixUnitaireHT;
    private double montantHT;
}

