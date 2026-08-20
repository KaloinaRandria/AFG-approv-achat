package afg.achat.afgApprovAchat.repository.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FournisseurRepo extends JpaRepository<Fournisseur, Integer>
{
    Boolean existsFournisseurByNomIgnoreCase(String nom);
}
