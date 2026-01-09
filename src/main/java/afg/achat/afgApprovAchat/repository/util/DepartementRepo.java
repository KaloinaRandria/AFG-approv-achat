package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.Departement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartementRepo extends JpaRepository<Departement,Integer> {
}
