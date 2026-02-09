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
    private String id;
    private String fournisseur;
    private String date;
    private String devise;
    private Double totalPrix;
    private List<ArticleLivraisonDTO> articles;
}
