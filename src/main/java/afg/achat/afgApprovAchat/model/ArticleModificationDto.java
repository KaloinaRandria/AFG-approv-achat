package afg.achat.afgApprovAchat.model;


import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ArticleModificationDto {
    private String codeArticle;
    private String designation;
    private Integer idUdm;
    private Integer idFamille;
    private Integer idCentreBudgetaire;
}
