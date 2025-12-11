package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.DeviseHistorique;
import afg.achat.afgApprovAchat.repository.util.DeviseHistoriqueRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeviseHistoriqueService {
    @Autowired
    DeviseHistoriqueRepo deviseHistoriqueRepo;

    public void saveDeviseHistorique(DeviseHistorique historique) {
        deviseHistoriqueRepo.save(historique);
    }

}
