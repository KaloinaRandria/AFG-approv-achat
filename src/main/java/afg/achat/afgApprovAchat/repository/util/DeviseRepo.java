package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.Devise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviseRepo extends JpaRepository<Devise, Integer> {

}
