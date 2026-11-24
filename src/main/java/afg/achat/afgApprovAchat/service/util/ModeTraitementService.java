package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.ModeTraitement;
import afg.achat.afgApprovAchat.repository.util.ModeTraitementRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModeTraitementService {
    private final ModeTraitementRepo modeTraitementRepo;

    public ModeTraitement[] getAllTraitements(){
        return modeTraitementRepo.findAll().toArray(new ModeTraitement[0]);
    }

    public void insertModeTraitement(ModeTraitement modeTraitement) throws Exception {
        try {
            this.modeTraitementRepo.save(modeTraitement);
        } catch (Exception e) {
            throw new Exception("Erreur lors de l'insertion du mode de traitement : " + e.getMessage());
        }
    }

    public ModeTraitement getModeTraitementById(int id) {
        return modeTraitementRepo.findById(id).orElse(null);
    }

    /**
     * Mode de traitement imposé par le type de demande ; {@code null} si le type n'en impose pas
     * (anciennes demandes) ou si le mode n'est pas paramétré en base.
     */
    public ModeTraitement getModeImpose(afg.achat.afgApprovAchat.model.util.TypeDemande type) {
        if (type == null || type.getModeTraitement() == null) return null;
        return modeTraitementRepo.findFirstByLibelleIgnoreCase(type.getModeTraitement().getLibelle()).orElse(null);
    }
}
