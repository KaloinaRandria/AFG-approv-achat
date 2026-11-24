package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BcContact;
import afg.achat.afgApprovAchat.repository.bonCommande.BcContactRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BcContactService {
    private final BcContactRepo bcContactRepo;

    public List<BcContact> getAllContacts() {
        return bcContactRepo.findAll();
    }

    public void insertContact(BcContact bcContact) {
        bcContactRepo.save(bcContact);
    }

}
