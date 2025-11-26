package afg.achat.afgApprovAchat.model.demande.bordereau;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.Transport;
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
@Table(name = "bordereau_mere")
public class BordereauMere {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_bordereau_mere")
    int id;
    @ManyToOne @JoinColumn(name = "id_demande_mere", referencedColumnName = "id_demande_mere")
    DemandeMere demandeMere;
    @ManyToOne @JoinColumn(name = "id_devise", referencedColumnName = "id_devise")
    Devise devise;
    @ManyToOne @JoinColumn(name = "id_transport", referencedColumnName = "id_transport")
    Transport transport;
    @Column(name = "date_bordereau")
    LocalDateTime dateBordereau;
    double poids;
    int colisage;
    String description;

    public void setDateBordereau(String dateBordereau) {
        this.dateBordereau = LocalDateTime.parse(dateBordereau);
    }

    public void setPoids(String poids) {
        this.poids = Double.parseDouble(poids);
    }

    public BordereauMere(DemandeMere demandeMere,  Devise devise, Transport transport,String dateBordereau ,String poids, String description) {
        this.setDemandeMere(demandeMere);
        this.setDevise(devise);
        this.setTransport(transport);
        this.setDateBordereau(dateBordereau);
        this.setPoids(poids);
        this.setDescription(description);
    }

}
