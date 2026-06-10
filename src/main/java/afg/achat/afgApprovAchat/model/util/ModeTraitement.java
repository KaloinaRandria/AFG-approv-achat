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
@Table(name = "mode_traitement")
public class ModeTraitement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_mode_traitement")
    int id;
    String libelle;
}
