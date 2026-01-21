package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.FournisseurHistorique;
import afg.achat.afgApprovAchat.repository.util.FournisseurHistoriqueRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FournisseurHistoriqueService {
    @Autowired
    FournisseurHistoriqueRepo fournisseurHistoriqueRepo;

    public void saveFournisseurHistorique(FournisseurHistorique fournisseurHistorique) {
       this.fournisseurHistoriqueRepo.save(fournisseurHistorique);
    }

    public FournisseurHistorique[] getFournisseurHistoriqueById(int id) {
        return this.fournisseurHistoriqueRepo.findFournisseurHistoriqueById(id);
    }
}
