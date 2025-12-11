package afg.achat.afgApprovAchat.model.util;

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
@Table(name = "devise_historique")
public class DeviseHistorique {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;
    @ManyToOne
    @JoinColumn(name = "id_devise", referencedColumnName = "id_devise", nullable = false)
    Devise devise;
    @Column(name = "ancien_cours_ariary")
    String ancienCoursAriary;
    @Column(name = "nouveau_cours_ariary")
    String nouvelleCoursAriary;
    @Column(name = "date_modification")
    String dateModification;
    @Column(name = "modifie_par")
    String modifiePar;

    public DeviseHistorique(Devise devise, String ancienCoursAriary, String nouvelleCoursAriary, String dateModification, String modifiePar) {
        this.devise = devise;
        this.ancienCoursAriary = ancienCoursAriary;
        this.nouvelleCoursAriary = nouvelleCoursAriary;
        this.dateModification = dateModification;
        this.modifiePar = modifiePar;
    }
}
