package afg.achat.afgApprovAchat.DTO;


import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ArticleModificationDTO {
    private String codeArticle;
    private String designation;
    private Integer idUdm;
    private Integer idFamille;
}
