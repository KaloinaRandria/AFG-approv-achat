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

    public Optional<Udm> getUdmById(int id) {
        return udmRepo.findById(id);
    }

    public void insertUdm(Udm udm) {
        if (udm.getAcronyme() == null || udm.getAcronyme().isBlank()) {
            throw new IllegalArgumentException("L'acronyme de l'unité de mesure est obligatoire.");
        }
        if (udm.getDescription() == null || udm.getDescription().isBlank()) {
            throw new IllegalArgumentException("La description de l'unité de mesure est obligatoire.");
        }
        if (udmRepo.existsByAcronyme(udm.getAcronyme())) {
            throw new IllegalArgumentException("Une unité de mesure avec cet acronyme existe déjà.");
        }
        udmRepo.save(udm);
    }
}
