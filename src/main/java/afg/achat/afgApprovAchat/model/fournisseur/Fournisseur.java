package afg.achat.afgApprovAchat.model.fournisseur;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import java.util.HashSet;
import java.util.Set;

/**
 * Indexé dans Lucene (Hibernate Search) pour la recherche instantanée de la liste,
 * comme les articles : nom en texte intégral (tolère une faute de frappe) et en mot-clé
 * (recherche partielle, tri) ; acronyme, mail et téléphone en mot-clé.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "fournisseur")
@Indexed
public class Fournisseur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_fournisseur")
    int id;
    @KeywordField(name = "contact_kw", normalizer = "lowercase")
    String contact;
    @KeywordField(name = "mail_kw", normalizer = "lowercase")
    String mail;
    @FullTextField(analyzer = "french")
    @KeywordField(name = "nom_kw", normalizer = "lowercase", sortable = Sortable.YES)
    String nom;
    @KeywordField(name = "acronyme_kw", normalizer = "lowercase")
    String acronyme;
    String description;
    String adressePhysique;

    public Fournisseur(String contact, String mail, String nom, String acronyme) {
        this.setContact(contact);
        this.setMail(mail);
        this.setNom(nom);
        this.setAcronyme(acronyme);
    }
}
