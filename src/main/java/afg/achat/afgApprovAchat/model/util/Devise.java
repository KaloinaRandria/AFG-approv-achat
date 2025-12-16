package afg.achat.afgApprovAchat.model.util;

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
@Table(name = "devise")
public class Devise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_devise")
    int id;
    @Column(unique = true)
    String acronyme;
    String designation;
    @Column(name = "cours_ariary")
    double coursAriary;
    @Column(name = "date_mise_a_jour")
    LocalDateTime dateMiseAJour;

    public Devise(String acronyme,String designation, String coursAriary, String dateMiseAJour) {
        this.setAcronyme(acronyme);
        this.setDesignation(designation);
        this.setCoursAriary(coursAriary);
        this.setDateMiseAJour(dateMiseAJour);
    }

    public void setCoursAriary(String coursAriary) {
        this.coursAriary = Double.parseDouble(coursAriary);
    }
    public void setDateMiseAJour(String dateMiseAJour) {
        this.dateMiseAJour = LocalDateTime.parse(dateMiseAJour);
    }
}
