package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.FamilleService;
import afg.achat.afgApprovAchat.service.FournisseurService;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileReader;
import java.io.InputStreamReader;

@Service
public class ImportService {
    @Autowired
    ArticleService articleService;
    @Autowired
    FamilleService familleService;
    @Autowired
    UdmService udmService;
    @Autowired
    FournisseurService fournisseurService;

    public void importCSVFamille(MultipartFile familleFile) {
        try (InputStreamReader reader = new InputStreamReader(familleFile.getInputStream());
             CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            String[] column;
            csvReader.readNext(); // Ignorer l'en-tête

            while ((column = csvReader.readNext()) != null) {
                Famille famille = new Famille();
                famille.setDescription(column[2].trim());

                familleService.saveFamilleIfNotExists(column[2]);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'importation du fichier CSV", e);
        }
    }

    public void importCSVFournisseur(MultipartFile fournisseurFile) {
        try (InputStreamReader reader = new InputStreamReader(fournisseurFile.getInputStream());
        CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            String[] column;
            csvReader.readNext(); // Ignorer l'en-tête

            while ((column = csvReader.readNext()) != null) {
                Fournisseur fournisseur = new Fournisseur();
                fournisseur.setNom(column[0].trim());
                String contact = "-";
                if (column.length > 1 && column[1] != null && !column[1].trim().isEmpty()) {
                    contact = column[1].trim();
                }
                fournisseur.setContact(contact);
                String mail = "-";
                if (column.length > 1 && column[2] != null && !column[2].trim().isEmpty()) {
                    mail = column[2].trim();
                }
                fournisseur.setMail(mail);

                fournisseurService.saveFournisseurIfNotExists(fournisseur);

            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'importation du fichier CSV", e);
        }
    }

    public void importCSVArticle(MultipartFile articleFile) {
        try (InputStreamReader reader = new InputStreamReader(articleFile.getInputStream());
             CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            String[] column;
            csvReader.readNext(); // Ignorer l'en-tête

            while ((column = csvReader.readNext()) != null) {

                Article article = new Article();
                article.setCodeArticle(column[0].trim());
                article.setDesignation(column[1].trim());
                article.setFamille(familleService.getFamilleByDesc(column[2].trim()));
                // 🔹 Gestion du seuil minimum
                int seuilMin = 0;
                if (column.length > 4 && column[4] != null && !column[4].trim().isEmpty()) {
                    seuilMin = Integer.parseInt(column[4].trim());
                }
                article.setSeuilMin(seuilMin);

                articleService.saveArticle(article);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'importation du fichier CSV", e);
        }
    }
}
