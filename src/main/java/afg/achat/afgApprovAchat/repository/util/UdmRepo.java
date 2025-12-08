package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.Udm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UdmRepo extends JpaRepository<Udm, Integer> {
}
