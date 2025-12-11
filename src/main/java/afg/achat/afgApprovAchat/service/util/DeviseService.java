package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.repository.util.DeviseRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeviseService {
    @Autowired
    DeviseRepo deviseRepo;

    public Devise[] getAllDevises() {
        return deviseRepo.findAll().toArray(new Devise[0]);
    }

    public void insertDevise(Devise devise) {
        deviseRepo.save(devise);
    }

    public Devise getDeviseById(int id) {
        return deviseRepo.findById(id).orElse(null);
    }
}
