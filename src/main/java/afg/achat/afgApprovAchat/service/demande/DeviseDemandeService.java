package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Devise d'une demande, choisie par les Moyens Généraux à leur étape (ariary par défaut).
 *
 * <p>Changer de devise :</p>
 * <ul>
 *   <li>vide les prix des lignes à acheter : un proforma dans une autre devise se ressaisit,
 *       il ne se déduit pas d'un prix converti ;</li>
 *   <li>convertit les prix des lignes servies depuis le stock (valorisées en ariary) avec le
 *       taux de la nouvelle devise, pour que la demande garde un seul total cohérent ;</li>
 *   <li>copie le cours actuel de la devise sur la demande.</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DeviseDemandeService {

    private final DemandeFilleRepo demandeFilleRepo;
    private final DemandeMereService demandeMereService;

    /**
     * Change la devise de la demande (les droits, étape M.G., sont contrôlés par l'appelant).
     *
     * @return nombre de lignes à acheter dont le prix est à ressaisir
     */
    public int changerDevise(DemandeMere demande, Devise nouvelle) {
        if (demande.getStatut() != StatutDemande.VALIDATION_N1) {
            throw new IllegalArgumentException("La devise ne se choisit qu'à l'étape des Moyens Généraux.");
        }
        String codeNouveau = nouvelle == null || nouvelle.getAcronyme() == null
                ? Devise.CODE_REFERENCE : nouvelle.getAcronyme();
        if (codeNouveau.equalsIgnoreCase(demande.getCodeDevise())) {
            return 0;
        }

        double ancienTaux = demande.getTauxEffectif();
        demande.appliquerDevise(nouvelle, LocalDateTime.now());

        int aRessaisir = 0;
        for (DemandeFille ligne : demandeFilleRepo.findDemandeFilleByDemandeMere(demande)) {
            if (ligne.getStatut() == StatutDemande.REFUSE) continue;
            if (ligne.getTypeApprovisionnement() == TypeApprovisionnement.STOCK) {
                // Prix FIFO : ancienne devise -> ariary -> nouvelle devise
                if (ligne.getPrixUnitaire() != null) {
                    ligne.setPrixUnitaire(demande.depuisAriary(ligne.getPrixUnitaire() * ancienTaux));
                }
            } else {
                ligne.setPrixUnitaire(null);
                ligne.setMontantEstime(null);
                aRessaisir++;
            }
            demandeFilleRepo.save(ligne);
        }
        demandeMereService.recalculerTotal(demande);
        return aRessaisir;
    }
}
