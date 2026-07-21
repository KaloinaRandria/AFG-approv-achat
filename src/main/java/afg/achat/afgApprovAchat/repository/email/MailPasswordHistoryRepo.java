package afg.achat.afgApprovAchat.repository.email;

import afg.achat.afgApprovAchat.email.MailPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MailPasswordHistoryRepo extends JpaRepository<MailPasswordHistory, Integer> {
    Optional<MailPasswordHistory> findByActifTrue();
    List<MailPasswordHistory> findAllByOrderByDateModificationDesc();
}
