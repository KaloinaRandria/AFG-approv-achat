package afg.achat.afgApprovAchat.repository.fournisseur;

import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** La recherche de la liste passe par l'index Lucene (FournisseurService.rechercher). */
@Repository
public interface FournisseurRepo extends JpaRepository<Fournisseur, Integer>
{
    Boolean existsFournisseurByNomIgnoreCase(String nom);
}
