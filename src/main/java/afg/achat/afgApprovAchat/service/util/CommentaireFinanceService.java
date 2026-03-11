package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.CommentaireFinance;
import afg.achat.afgApprovAchat.repository.util.CommentaireFinanceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommentaireFinanceService {
    @Autowired
    CommentaireFinanceRepo commentaireFinanceRepo;

    public void insertCommentaireFinance(CommentaireFinance commentaireFinance) {
        this.commentaireFinanceRepo.save(commentaireFinance);
    }

    public CommentaireFinance getCommentaireFinanceByIdDemande(DemandeMere demandeMere) {
       return this.commentaireFinanceRepo.findCommentaireFinanceByDemandeMere(demandeMere);
    }
}
