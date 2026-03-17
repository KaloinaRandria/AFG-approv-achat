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
                                       int prochaineEtape) {
        Map<String, Object> props = new HashMap<>();
        props.put("id",            demande.getId());
        props.put("demandeur",     demande.getDemandeur());
        props.put("validateur",    validateur);
        props.put("commentaire",   commentaire);
        props.put("dateDecision",  LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        props.put("etape",         StatutDemande.getLibelle(etapeCourante));
        props.put("prochaineEtape", StatutDemande.getLibelle(prochaineEtape));

        Mail mail = new Mail(
                "validationDemande",
                demande.getDemandeur().getMail(),
                "[AFG/MADA] - Votre demande a été validée",
                props
        );
        this.sendEmail(mail);
    }
}
