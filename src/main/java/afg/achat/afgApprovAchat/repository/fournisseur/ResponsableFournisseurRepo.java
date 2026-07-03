package afg.achat.afgApprovAchat.repository.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.ResponsableFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResponsableFournisseurRepo extends JpaRepository<ResponsableFournisseur, Long> {
    Optional<ResponsableFournisseur> findFirstByFournisseur_IdOrderByDateAffectationDesc(int idFournisseur);
}
