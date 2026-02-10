package afg.achat.afgApprovAchat.model.util;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
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
@Table(name = "piece_jointe")
public class PieceJointe {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_piece_jointe")
    int id;
    String nomFichier;
    @ManyToOne @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;
}
