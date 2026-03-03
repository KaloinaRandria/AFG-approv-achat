package afg.achat.afgApprovAchat.service.demande.bonSortie;

import afg.achat.afgApprovAchat.repository.demande.bonSortie.BonSortieMereRepo;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BonSortieMereService {
    @Autowired
    BonSortieMereRepo bonSortieMereRepo;
}
