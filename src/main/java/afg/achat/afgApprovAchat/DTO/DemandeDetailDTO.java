package afg.achat.afgApprovAchat.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DemandeDetailDTO {
    String id;
    String demandeur;
    LocalDateTime dateDemande;
    String typeDemande;
    String statutDemande;
    List<ArticleDemandeDTO> articles;
}
