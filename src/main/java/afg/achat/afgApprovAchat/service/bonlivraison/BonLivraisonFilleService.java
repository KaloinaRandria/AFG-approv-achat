package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonFilleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BonLivraisonFilleService {
    @Autowired
    BonLivraisonFilleRepo bonLivraisonFilleRepo;

    public void insertBonLivraisonFilleList(BonLivraisonFille bonLivraisonFille) {
        this.bonLivraisonFilleRepo.save(bonLivraisonFille);
    }

    public BonLivraisonFille[] getAllBonLivraisonFilles() {
        return bonLivraisonFilleRepo.findAll().toArray(new BonLivraisonFille[0]);
    }

    public Optional<BonLivraisonFille> getBonLivraisonFilleById(int id) {
        return bonLivraisonFilleRepo.findById(id);
    }

    public List<BonLivraisonFille> getBonLivraisonFillesByMereId(int idMere) {
        return bonLivraisonFilleRepo.findBonLivraisonFilleByBonLivraisonMere(idMere);
    }
}
