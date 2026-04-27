package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepo extends JpaRepository<Service,Integer> {
}
