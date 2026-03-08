package afg.achat.afgApprovAchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "centre_budgetaire")
public class CentreBudgetaire {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_centre_budgetaire")
    int id;
    String codeCentre;
    String description;
    @ManyToOne @JoinColumn(name = "code_parent", referencedColumnName = "id_centre_budgetaire")
    CentreBudgetaire codeParent;
    Integer niveau;
    @ManyToOne @JoinColumn(name = "id_agence", referencedColumnName = "id_agence")
    Agence agence;
    public CentreBudgetaire(String codeCentre){
        this.setCodeCentre(codeCentre);
    }
}
