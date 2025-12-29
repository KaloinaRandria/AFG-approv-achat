package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.Departement;
import afg.achat.afgApprovAchat.repository.util.DepartementRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DepartementService {
    @Autowired
    DepartementRepo departementRepo;

    public Departement[] getAllDepartements() {
        return departementRepo.findAll().toArray(new Departement[0]);
    }
}
