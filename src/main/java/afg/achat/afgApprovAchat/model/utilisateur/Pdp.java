package afg.achat.afgApprovAchat.model.utilisateur;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Pdp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_pdp")
    private Integer id;
    @Column(columnDefinition = "TEXT")
    private String thumbnail;
    private String original;
    public Pdp(String thumbnail, String original) {
        super();
        this.thumbnail = thumbnail;
        this.original = original;
    }
    public Pdp() {
        super();
    }
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getThumbnail() {
        return thumbnail;
    }
    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
    public String getOriginal() {
        return original;
    }
    public void setOriginal(String original) {
        this.original = original;
    }

}
