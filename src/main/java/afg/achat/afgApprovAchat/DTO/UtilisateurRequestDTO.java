package afg.achat.afgApprovAchat.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UtilisateurRequestDTO {
    private String nom;
    private String prenom;
    private String mail;
    private String contact;

    private Set<Integer> roleIds;
    private Integer superieurHierarchiqueId;  // nullable
    private Integer pdpId;                    // nullable
    private Integer posteId;                  // nullable
    private Integer serviceId;               // nullable
    private Set<Integer> validateurIds;       // nullable

}
