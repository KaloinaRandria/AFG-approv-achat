package afg.achat.afgApprovAchat.repository.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.ResponsableFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResponsableFournisseurRepo extends JpaRepository<ResponsableFournisseur, Integer> {
    @Query("select rf from ResponsableFournisseur rf where rf.fournisseur.id = :idFournisseur order by rf.dateAffectation desc limit 1")
    Optional<ResponsableFournisseur> findFirstByFournisseur_IdOrderByDateAffectationDesc(int idFournisseur);
}
