package afg.achat.afgApprovAchat.model.demande.bonSortie;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bon_sortie_mere")
@SequenceGenerator(
        name = "bon_sortie_mere_id_bon_sortie_mere_seq",
        sequenceName = "bon_sortie_mere_id_bon_sortie_mere_seq",
        allocationSize = 1
)
public class BonSortieMere {
    @Id @Column(name = "id_bon_sortie_mere")
    String id;
    @ManyToOne @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;
    @Column(name = "date_sortie")
    LocalDateTime dateSortie;
    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    Statut statut = Statut.CREE;

    public enum Statut {
        CREE,       // BS créé mais non confirmé
        PARTIELLE,  // BS confirmé partiellement (certaines lignes en attente)
        VALIDEE     // BS confirmé entièrement
    }

    public void setId(IdGenerator idGenerator) {
        this.id = idGenerator.generateId("BS", "bon_sortie_mere_id_bon_sortie_mere_seq");
    }
}
