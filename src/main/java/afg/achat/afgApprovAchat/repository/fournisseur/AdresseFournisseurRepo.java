package afg.achat.afgApprovAchat.repository.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.Adresse;
import afg.achat.afgApprovAchat.model.fournisseur.AdresseFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdresseFournisseurRepo extends JpaRepository<AdresseFournisseur, Integer> {
    @Query("select af from AdresseFournisseur af where af.fournisseur.id = :idFournisseur order by af.dateAffectation desc limit 1")
    Optional<AdresseFournisseur> findFirstByFournisseur_IdOrderByDateAffectationDesc(int idFournisseur);
}
