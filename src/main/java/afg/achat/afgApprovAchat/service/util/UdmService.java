package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.repository.util.UdmRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UdmService {
    @Autowired
    UdmRepo udmRepo;

    public Udm[] getAllUdms() {
        return udmRepo.findAll().toArray(new Udm[0]);
    }

    public Optional<Udm> getUdmById(Integer id) {
        return udmRepo.findById(id);
    }
}
