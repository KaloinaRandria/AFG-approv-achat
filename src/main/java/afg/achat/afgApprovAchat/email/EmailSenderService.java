package afg.achat.afgApprovAchat.email;

import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EmailSenderService {

    private final JavaMailSender emailSender;
    private final TemplateEngine templateEngine;

    // Pool de threads limité pour l'envoi asynchrone
    private final ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    public EmailSenderService(JavaMailSender emailSender, TemplateEngine templateEngine) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
    }

    public void sendEmail(Mail mail) {
        emailExecutor.submit(() -> {
            try {
                MimeMessage message = emailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                        StandardCharsets.UTF_8.name());

                Context context = new Context();
                context.setVariables(mail.getProps());
                String html = templateEngine.process("email/" + mail.getTemplateName(), context);

                String[] recipients = mail.getMailTo().split(",");
                for (String recipient : recipients) {
                    helper.addTo(recipient.trim());
                }

                if (mail.getCc() != null) {
                    helper.setCc(mail.getCc());
                }

                helper.setSubject(mail.getSubject());
                helper.setFrom(mail.getFrom_adress() != null ? mail.getFrom_adress() :
                        ((JavaMailSenderImpl) emailSender).getUsername());
                helper.setText(html, true);

                if (mail.getAttachments() != null) {
                    for (MultipartFile file : mail.getAttachments()) {
                        helper.addAttachment(file.getOriginalFilename(),
                                new ByteArrayResource(file.getBytes()));
                    }
                }

                ClassPathResource imageFile = new ClassPathResource("static/img/logo_afg_email.png");
                helper.addInline("signature-image", new FileSystemResource(imageFile.getFile()));

                System.out.println("Sending mail to: " + mail.getMailTo());
                emailSender.send(message);
                System.out.println("Email sent to: " + mail.getMailTo());

            } catch (Exception e) {
                System.err.println("Failed to send email to: " + mail.getMailTo());
                e.printStackTrace();
            }
        });
    }

    public void envoyerMailValidation(DemandeMere demande,
                                      Utilisateur validateur,
                                      String commentaire,
                                      int etapeCourante,
                                      int prochaineEtape,
                                      Utilisateur prochainValidateur) {
        System.out.println(">>> Mail demandeur");
        System.out.println("    to            : " + demande.getDemandeur().getMail());
        System.out.println("    etape         : " + StatutDemande.getLibelle(etapeCourante));
        System.out.println("    prochaineEtape: " + StatutDemande.getLibelle(prochaineEtape));
        System.out.println("    validateur    : " + validateur.getNom());

        String dateDecision = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        // ── Mail 1 : demandeur informé de la validation ──────────────────────
        Map<String, Object> propsDemandeur = new HashMap<>();
        propsDemandeur.put("id",             demande.getId());
        propsDemandeur.put("demandeur",      demande.getDemandeur());
        propsDemandeur.put("validateur",     validateur);
        propsDemandeur.put("commentaire",    commentaire);
        propsDemandeur.put("dateDecision",   dateDecision);
        propsDemandeur.put("etape",          StatutDemande.getLibelle(etapeCourante));
        propsDemandeur.put("prochaineEtape", StatutDemande.getLibelle(prochaineEtape));

        this.sendEmail(new Mail(
                "demandeValid",
                demande.getDemandeur().getMail(),
                "[AFG Bank - Demande Achat] - Votre demande a été validée",
                propsDemandeur
        ));

        // ── Mail 2 : prochain validateur notifié ─────────────────────────────
        if (prochainValidateur != null && prochainValidateur.getMail() != null) {
            Map<String, Object> propsValidateur = new HashMap<>();
            propsValidateur.put("id",             demande.getId());
            propsValidateur.put("demandeur",      demande.getDemandeur());
            propsValidateur.put("validateur",     validateur);
            propsValidateur.put("destinataire",   prochainValidateur);
            propsValidateur.put("dateDecision",   dateDecision);
            propsValidateur.put("etape",          StatutDemande.getLibelle(etapeCourante));
            propsValidateur.put("prochaineEtape", StatutDemande.getLibelle(prochaineEtape));
            String baseUrl = "http://10.25.10.151:8081/AFG-approv-achat";
//                String baseUrl = "http://localhost:8080";

            String lienValidation = baseUrl + "/demande/fiche/" + demande.getId();
            propsValidateur.put("lienValidation", lienValidation);

            this.sendEmail(new Mail(
                    "notificationValidateur",
                    prochainValidateur.getMail(),
                    "[AFG Bank - Demande Achat] - Demande en attente de votre validation",
                    propsValidateur
            ));
        }
    }

    // Mail unique au demandeur
    public void envoyerMailDemandeur(DemandeMere demande,
                                     Utilisateur validateur,
                                     String commentaire,
                                     int etapeCourante,
                                     int prochaineEtape) {
        String dateDecision = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        Map<String, Object> props = new HashMap<>();
        props.put("id",             demande.getId());
        props.put("demandeur",      demande.getDemandeur());
        props.put("validateur",     validateur);
        props.put("commentaire",    commentaire);
        props.put("dateDecision",   dateDecision);
        props.put("etape",          StatutDemande.getLibelle(etapeCourante));
        props.put("prochaineEtape", StatutDemande.getLibelle(prochaineEtape));

        this.sendEmail(new Mail(
                "demandeValid",
                demande.getDemandeur().getMail(),
                "[AFG Bank - Demande Achat] - Votre demande a été validée",
                props
        ));
    }

    // Mail individuel au validateur suivant (pas de mail demandeur)
    public void envoyerMailValidateur(DemandeMere demande,
                                      Utilisateur validateur,
                                      int etapeCourante,
                                      int prochaineEtape,
                                      Utilisateur prochainValidateur) {
        if (prochainValidateur == null || prochainValidateur.getMail() == null) return;

        String dateDecision = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String baseUrl = "http://10.25.10.151:8081/AFG-approv-achat";

        Map<String, Object> props = new HashMap<>();
        props.put("id",             demande.getId());
        props.put("demandeur",      demande.getDemandeur());
        props.put("validateur",     validateur);
        props.put("destinataire",   prochainValidateur);
        props.put("dateDecision",   dateDecision);
        props.put("etape",          StatutDemande.getLibelle(etapeCourante));
        props.put("prochaineEtape", StatutDemande.getLibelle(prochaineEtape));
        props.put("lienValidation", baseUrl + "/demande/fiche/" + demande.getId());

        this.sendEmail(new Mail(
                "notificationValidateur",
                prochainValidateur.getMail(),
                "[AFG Bank - Demande Achat] - Demande en attente de votre validation",
                props
        ));
    }
}
