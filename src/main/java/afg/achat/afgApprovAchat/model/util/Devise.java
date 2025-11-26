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
@Table(name = "devise")
public class Devise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_devise")
    int id;
    String acronyme;
    String designation;
    @Column(name = "cours_ariary")
    double coursAriary;

    public Devise(String acronyme,String designation, String coursAriary) {
        this.setAcronyme(acronyme);
        this.setDesignation(designation);
        this.setCoursAriary(coursAriary);
    }

    public void setCoursAriary(String coursAriary) {
        this.coursAriary = Double.parseDouble(coursAriary);
    }
}
