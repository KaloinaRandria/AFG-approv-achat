package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.utilisateur.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepo extends JpaRepository<Role,Integer> {
}
