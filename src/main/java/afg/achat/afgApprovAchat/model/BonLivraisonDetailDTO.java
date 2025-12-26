package afg.achat.afgApprovAchat.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BonLivraisonDetailDTO {
    private int id;
    private String fournisseur;
    private String date;
    private String devise;
    private List<ArticleLivraisonDTO> articles;
}
