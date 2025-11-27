package afg.achat.afgApprovAchat.model.stock.gisement;

import afg.achat.afgApprovAchat.model.util.Local;
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
@Table(name = "existant_gisement")
public class ExistantGisement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_gisement")
    int id;
    @ManyToOne @JoinColumn(name = "id_local", referencedColumnName = "id_local")
    Local local;
    int trave;
    String alveole;
    int etagere;
    int bac;

    public ExistantGisement(Local local, int trave, String alveole, int etagere, int bac) {
        this.setLocal(local);
        this.setTrave(trave);
        this.setAlveole(alveole);
        this.setEtagere(etagere);
        this.setBac(bac);
    }
}
