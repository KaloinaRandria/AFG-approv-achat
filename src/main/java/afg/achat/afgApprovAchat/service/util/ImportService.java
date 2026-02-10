package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonFilleRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.FamilleService;
import afg.achat.afgApprovAchat.service.FournisseurService;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonMereService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Service
public class ImportService {
    @Autowired
    ArticleService articleService;
    @Autowired
    FamilleService familleService;
    @Autowired
    DeviseService deviseService;
    @Autowired
    FournisseurService fournisseurService;
    @Autowired
    IdGenerator idGenerator;
    @Autowired
    BonLivraisonMereService bonLivraisonMereService;
    @Autowired
    BonLivraisonFilleRepo bonLivraisonFilleRepo;
    @Autowired
    UdmService udmService;
    @Autowired
    StockMereService stockMereService;

    public void importCSVFamille(MultipartFile familleFile) {
        try (InputStreamReader reader = new InputStreamReader(familleFile.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            String[] column;
            csvReader.readNext(); // Ignorer l'en-tête

            while ((column = csvReader.readNext()) != null) {
                Famille famille = new Famille();
                famille.setDescription(column[0].trim());

                familleService.saveFamilleIfNotExists(column[0].trim());
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'importation du fichier CSV", e);
        }
    }

    public void importCSVFournisseur(MultipartFile fournisseurFile) {
        try (InputStreamReader reader = new InputStreamReader(fournisseurFile.getInputStream(), StandardCharsets.UTF_8);
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
        try (InputStreamReader reader = new InputStreamReader(articleFile.getInputStream(), StandardCharsets.ISO_8859_1);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            String[] column;
            csvReader.readNext(); // Ignorer l'en-tête

            while ((column = csvReader.readNext()) != null) {

                Article article = new Article();
                article.setCodeArticle(idGenerator);
                article.setCodeProvisoire(column[0].trim());
                article.setDesignation(column[1].trim());
                article.setFamille(familleService.getFamilleByDesc(column[2].trim()));
                // 🔹 Gestion du seuil minimum
                int seuilMin = 0;
                if (column.length > 4 && column[4] != null && !column[4].trim().isEmpty()) {
                    seuilMin = Integer.parseInt(column[4].trim());
                }
                article.setSeuilMin(seuilMin);
                String[] finalColumn = column;
                article.setUdm(udmService.getUdmById(Integer.parseInt(column[6].trim()))
                        .orElseThrow(() -> new RuntimeException("Udm non trouvée avec l'id: " + finalColumn[6].trim())));

                articleService.saveArticle(article);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'importation du fichier CSV", e);
        }
    }


    public void importCSVBLMere(MultipartFile factureFile) {
        try (InputStreamReader reader = new InputStreamReader(factureFile.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                     .build()) {

            String[] column;
            csvReader.readNext(); // Ignorer l'en-tête

            while ((column = csvReader.readNext()) != null) {

                String idFacture = column[0].trim();
                if (idFacture.isEmpty()) continue;

                // ✅ Vérification : ne pas réinsérer si facture déjà importée
                if (bonLivraisonMereService.existsByIdFacture(idFacture)) {
                    continue; // ou log: "déjà existant"
                }

                BonLivraisonMere bonLivraisonMere = new BonLivraisonMere();
                bonLivraisonMere.setId(idGenerator);
                bonLivraisonMere.setIdFacture(idFacture);

                bonLivraisonMere.setDevise(
                        deviseService.getDeviseById(1)
                                .orElseThrow(() -> new RuntimeException("Devise non trouvée avec l'id: 1"))
                );

                bonLivraisonMere.setDescription("Bon de livraison importé - Facture N° " + idFacture);

                bonLivraisonMereService.insertBonLivraisonMere(bonLivraisonMere);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'importation du fichier CSV", e);
        }
    }

    @Transactional
    public void importCSVBLFille(MultipartFile file) {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                     .build()) {

            String[] column;
            csvReader.readNext(); // header

            while ((column = csvReader.readNext()) != null) {

                String faNo = safe(column, 15);       // FA N°
                String code = safe(column, 2);        // CODE article
                String qteStr = safe(column, 9);      // Quantité
                String puStr = safe(column, 10);       // PU (peut être vide)

                if (faNo.isEmpty() || code.isEmpty() || qteStr.isEmpty()) continue;

                // 1) Trouver le BL Mère (id_bl_mere = FA N°)
                BonLivraisonMere blMere = bonLivraisonMereService.getBonLivraisonMereByIdFacture(faNo);

                // 2) Trouver l’article
                Article article = articleService.getArticleByCodeProvisoire(code);
                // 3) Convertir les valeurs numériques
                double qte = parseDoubleFlexible(qteStr);
                double pu = puStr.isEmpty() ? 0.0 : parseDoubleFlexible(puStr);

                // ✅ Option A (simple) : on insère une ligne par ligne CSV (même si doublon)
                 BonLivraisonFille blFille = new BonLivraisonFille();
                 blFille.setBonLivraisonMere(blMere);
                 blFille.setArticle(article);
                 blFille.setQuantiteRecu(String.valueOf(qte));
                 blFille.setQuantiteDemande(String.valueOf(qte));
                 blFille.setPrixUnitaire(String.valueOf(pu));
                 bonLivraisonFilleRepo.save(blFille);

                // ✅ Option B (recommandé) : si même (BL, article) existe, on cumule la quantité
//                BonLivraisonFille blFille = bonLivraisonFilleRepo
//                        .findByBonLivraisonMereAndArticle(blMere, article)
//                        .orElseGet(() -> {
//                            BonLivraisonFille n = new BonLivraisonFille();
//                            n.setBonLivraisonMere(blMere);
//                            n.setArticle(article);
//                            n.setPrixUnitaire(String.valueOf(pu));
//                            n.setQuantiteRecu("0");
//                            n.setQuantiteDemande("0");
//                            return n;
//                        });
//
//                blFille.setPrixUnitaire(String.valueOf(pu)); // au cas où ça change
//                blFille.setQuantiteRecu(String.valueOf(blFille.getQuantiteRecu() + qte));
//                blFille.setQuantiteDemande(String.valueOf(blFille.getQuantiteDemande() + qte));
//
//                bonLivraisonFilleRepo.save(blFille);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur import BL Fille CSV", e);
        }
    }

    private String safe(String[] row, int idx) {
        if (row == null || idx < 0 || idx >= row.length || row[idx] == null) return "";
        return row[idx].trim();
    }

    private double parseDoubleFlexible(String s) {
        String cleaned = s.trim()
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace(",", ".");
        return Double.parseDouble(cleaned);
    }

    @Transactional
    public void importCSVAchat(MultipartFile achatFile) {
        try (InputStreamReader reader = new InputStreamReader(achatFile.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                     .build()) {

            csvReader.readNext(); // header
            String[] row;

            // Cache par facture pour éviter 1000 requêtes
            Map<String, BonLivraisonMere> blCache = new HashMap<>();
            Map<String, Double> totalCache = new HashMap<>();

            while ((row = csvReader.readNext()) != null) {

                String idFacture = safe(row, 15);    // ✅ adapte
                String codeArt   = safe(row, 2);    // ✅ adapte (code provisoire)
                String qteStr    = safe(row, 9);    // ✅ adapte
                String puStr     = safe(row, 10);    // ✅ adapte
                String dateStr   = safe(row, 1);    // ✅ adapte si tu as une date, sinon ""

                if (idFacture.isEmpty() || codeArt.isEmpty() || qteStr.isEmpty()) continue;

                double qte = parseDoubleFlexible(qteStr);
                double pu  = puStr.isEmpty() ? 0.0 : parseDoubleFlexible(puStr);

                // 1) BL MERE (get or create)
                BonLivraisonMere blMere = blCache.get(idFacture);
                if (blMere == null) {
                    if (bonLivraisonMereService.existsByIdFacture(idFacture)) {
                        blMere = bonLivraisonMereService.getBonLivraisonMereByIdFacture(idFacture);
                    } else {
                        blMere = new BonLivraisonMere();
                        blMere.setId(idGenerator);
                        blMere.setIdFacture(idFacture);

                        blMere.setDevise(deviseService.getDeviseById(1)
                                .orElseThrow(() -> new RuntimeException("Devise non trouvée id=1")));

                        blMere.setDescription("BL importé depuis achat - Facture N° " + idFacture);

                        // date réception
                        if (!dateStr.isEmpty()) {
                            blMere.setDateReception(String.valueOf(parseDateReception(dateStr)));
                            // si format ISO-8601
                        } else {
                            blMere.setDateReception(String.valueOf(java.time.LocalDateTime.now()));
                        }

                        blMere.setTotalPrix(0.0);
                        bonLivraisonMereService.insertBonLivraisonMere(blMere);
                    }

                    blCache.put(idFacture, blMere);
                    totalCache.put(idFacture, blMere.getTotalPrix() == null ? 0.0 : blMere.getTotalPrix());
                }

                // 2) Article
                Article article = articleService.getArticleByCodeProvisoire(codeArt);

                // 3) Update dernier prix article (dernier prix = celui de la dernière ligne lue)
                if (pu > 0) {
                    article.setPrixUnitaire(String.valueOf(pu));
                    // si tu veux éviter save à chaque ligne, tu peux laisser JPA dirty-checker,
                    // mais safe: articleService.saveArticle(article) ou articleRepo.save(article)
                    articleService.saveArticle(article);
                }

                // 4) BL Fille (cumule)
                BonLivraisonMere finalBlMere = blMere;
                BonLivraisonFille blFille = bonLivraisonFilleRepo
                        .findByBonLivraisonMereAndArticle(blMere, article)
                        .orElseGet(() -> {
                            BonLivraisonFille n = new BonLivraisonFille();
                            n.setBonLivraisonMere(finalBlMere);
                            n.setArticle(article);
                            n.setPrixUnitaire(String.valueOf(pu));
                            n.setQuantiteRecu("0");
                            n.setQuantiteDemande("0");
                            return n;
                        });

                // on garde le dernier PU (celui importé le plus récent)
                if (pu > 0) blFille.setPrixUnitaire(String.valueOf(pu));

                blFille.setQuantiteRecu(String.valueOf(blFille.getQuantiteRecu() + qte));
                blFille.setQuantiteDemande(String.valueOf(blFille.getQuantiteDemande() + qte));
                bonLivraisonFilleRepo.save(blFille);

                // 5) Stock : entrée
                StockMere stockMere = stockMereService.getOrCreateStockMere(blMere);
                stockMereService.addEntree(stockMere, article, qte);

                // 6) Total BL
                double ligneTotal = qte * pu;
                totalCache.put(idFacture, totalCache.get(idFacture) + ligneTotal);
            }

            // ✅ à la fin : update totalPrix de chaque BL
            for (Map.Entry<String, BonLivraisonMere> e : blCache.entrySet()) {
                String idFacture = e.getKey();
                BonLivraisonMere bl = e.getValue();
                bl.setTotalPrix(totalCache.get(idFacture));
                bonLivraisonMereService.insertBonLivraisonMere(bl); // ou save/update selon ton service
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'importation achat (BL + Stock)", e);
        }
    }

    private LocalDateTime parseDateReception(String s) {
        if (s == null) return LocalDateTime.now();
        String v = s.trim();
        if (v.isEmpty()) return LocalDateTime.now();

        // 1) dd/MM/yyyy
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate d = LocalDate.parse(v, f);
            return d.atStartOfDay();
        } catch (DateTimeParseException ignored) {}

        // 2) dd/MM/yyyy HH:mm
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return LocalDateTime.parse(v, f);
        } catch (DateTimeParseException ignored) {}

        // 3) ISO fallback (2024-11-26T10:15:00)
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {}

        // fallback final
        return LocalDateTime.now();
    }

}
