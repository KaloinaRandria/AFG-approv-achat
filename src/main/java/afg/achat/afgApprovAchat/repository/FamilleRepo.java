package afg.achat.afgApprovAchat.repository;

import afg.achat.afgApprovAchat.model.Famille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FamilleRepo extends JpaRepository<Famille, Integer> {
}
