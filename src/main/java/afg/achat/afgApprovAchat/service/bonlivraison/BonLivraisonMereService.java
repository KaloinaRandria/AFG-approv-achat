package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonMereRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BonLivraisonMereService {
    @Autowired
    BonLivraisonMereRepo bonLivraisonMereRepo;

    public BonLivraisonMere[] getAllBonLivraisonMeres() {
        return bonLivraisonMereRepo.findAll().toArray(new BonLivraisonMere[0]);
    }
}
