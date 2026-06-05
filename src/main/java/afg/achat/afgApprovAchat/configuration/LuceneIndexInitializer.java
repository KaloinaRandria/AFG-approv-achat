package afg.achat.afgApprovAchat.configuration;

import afg.achat.afgApprovAchat.model.Article;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LuceneIndexInitializer implements ApplicationRunner {

    @Autowired
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        SearchSession searchSession = Search.session((Session) entityManager);
        searchSession.massIndexer(Article.class)
                .threadsToLoadObjects(4)
                .startAndWait();
    }
}