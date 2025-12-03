//package afg.achat.afgApprovAchat.service.simulationstock;
//
//import afg.achat.afgApprovAchat.model.Article;
//import afg.achat.afgApprovAchat.model.stock.gisement.ExistantGisement;
//import afg.achat.afgApprovAchat.model.stock.gisement.GisementStockFille;
//import afg.achat.afgApprovAchat.model.util.Local;
//import afg.achat.afgApprovAchat.repository.ArticleRepo;
//import afg.achat.afgApprovAchat.repository.stock.gisement.GisementRepo;
//import afg.achat.afgApprovAchat.repository.stock.gisement.GisementStockFilleRepo;
//import afg.achat.afgApprovAchat.repository.util.LocalRepo;
//import afg.achat.afgApprovAchat.service.util.IdGenerator;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//
//@Component
//public class StockSimulationSeeder implements CommandLineRunner {
//   @Autowired
//    ArticleRepo articleRepo;
//   @Autowired
//    GisementRepo gisementRepo;
//   @Autowired
//    GisementStockFilleRepo gisementStockFilleRepo;
//   @Autowired
//   LocalRepo localRepo;
//   @Autowired
//   IdGenerator idGenerator;
//    @Override
//    public void run(String... args) throws Exception {
//
//        // 🔐 Sécurité : On ne régénère rien si des données existent déjà
//        if (articleRepo.count() > 0) {
//            System.out.println("Simulation déjà existante → pas de regénération.");
//            return;
//        }
//
//
//        for (int i = 0; i <= 10; i++) {
//            Article article = new Article();
//            article.setDesignation("Article " + i);
//            article.setSeuilMin(10);
//            article.setCodeArticle(idGenerator);
//            articleRepo.save(article);
//        }
//
//        Local local = new Local("Depot Principal");
//        localRepo.save(local);
//
//        ExistantGisement existantGisement = new  ExistantGisement();
//        existantGisement.setLocal(local);
//        existantGisement.setTrave("01");
//        existantGisement.setAlveole("A");
//        existantGisement.setEtagere("01");
//        existantGisement.setBac("01");
//        gisementRepo.save(existantGisement);
//
//        for (Article article : articleRepo.findAll()) {
//            GisementStockFille gisementStockFille = new GisementStockFille();
//            gisementStockFille.setArticle(article);
//            gisementStockFille.setExistantGisement(existantGisement);
//            gisementStockFille.setQuantiteIn("50");
//            gisementStockFille.setQuantiteOut("0");
//            gisementStockFille.setDateMouvement(String.valueOf(LocalDateTime.now().minusDays(10)));
//            gisementStockFilleRepo.save(gisementStockFille);
//        }
//
//        for (Article article : articleRepo.findAll()) {
//            GisementStockFille gisementStockFille = new GisementStockFille();
//            gisementStockFille.setArticle(article);
//            gisementStockFille.setExistantGisement(existantGisement);
//            gisementStockFille.setQuantiteIn("0");
//            gisementStockFille.setQuantiteOut("20");
//            gisementStockFille.setDateMouvement(String.valueOf(LocalDateTime.now().minusDays(5)));
//            gisementStockFilleRepo.save(gisementStockFille);
//        }
//
//        System.out.println("Simulation de stock générée !");
//
//    }
//}
