package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonFilleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BonLivraisonFilleService {
    @Autowired
    BonLivraisonFilleRepo bonLivraisonFilleRepo;

    public void insertBonLivraisonFilleList(BonLivraisonFille bonLivraisonFille) {
        this.bonLivraisonFilleRepo.save(bonLivraisonFille);
    }
}
