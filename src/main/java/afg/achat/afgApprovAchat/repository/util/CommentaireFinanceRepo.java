package afg.achat.afgApprovAchat.repository.util;

import afg.achat.afgApprovAchat.model.util.CommentaireFinance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentaireFinanceRepo extends JpaRepository<CommentaireFinance,Integer> {
}
