package afg.achat.afgApprovAchat.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ArticleLivraisonDTO {
    private String designation;
    private Double quantite;
    private Double prixUnitaire;
}