package afg.achat.afgApprovAchat.email;

import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class EmailSenderService {

    private final JavaMailSender emailSender;
    private final TemplateEngine templateEngine;

    // Pool de threads limité pour l'envoi asynchrone
    private final ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    /** URL publique de l'application (liens des mails). */
    @Value("${app.base-url}")
    private String baseUrl;

    /** Lien vers la fiche d'une demande, pour les mails. */
    public String lienFiche(String demandeId) {
        return baseUrl + "/demande/fiche/" + demandeId;
    }

    /**
     * Interrupteur d'envoi des mails. {@code true} par défaut (production) ;
     * le profil {@code dev} le met à {@code false} : les mails sont alors seulement tracés dans les logs.
     */
    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    public EmailSenderService(JavaMailSender emailSender, TemplateEngine templateEngine) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
    }

    @PostConstruct
    void signalerModeMail() {
        if (!mailEnabled) {
            log.warn("ENVOI DES MAILS DÉSACTIVÉ (app.mail.enabled=false) : les mails sont seulement écrits dans les logs.");
        }
    }

    public void sendEmail(Mail mail) {
        if (!mailEnabled) {
            log.info("[MAIL NON ENVOYÉ] modèle={} | à={} | sujet={}",
                    mail.getTemplateName(), mail.getMailTo(), mail.getSubject());
            return;
        }

        emailExecutor.submit(() -> {
            try {
                MimeMessage message = emailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                        StandardCharsets.UTF_8.name());

                Context context = new Context();
                context.setVariables(mail.getProps());
                String html = templateEngine.process("email/" + mail.getTemplateName(), context);

                for (String recipient : mail.getMailTo().split(",")) {
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

                // ClassPathResource directement : fonctionne aussi depuis un jar/war
                helper.addInline("signature-image", new ClassPathResource("static/img/logo_afg_email.png"));

                emailSender.send(message);
                log.info("Mail envoyé à {} ({})", mail.getMailTo(), mail.getTemplateName());

            } catch (Exception e) {
                log.error("Échec de l'envoi du mail à {} ({})", mail.getMailTo(), mail.getTemplateName(), e);
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

    // ── Notifications Paiement Direct ─────────────────────────────────────────

    /**
     * Notifie tous les utilisateurs Finance qu'une demande de paiement direct
     * leur a été transmise.
     *
     * @param demande       la demande concernée
     * @param transmetteur  l'utilisateur qui a transmis la demande
     * @param commentaire   commentaire éventuel de transmission
     * @param financeUsers  liste des utilisateurs ayant ROLE_FINANCE
     */
    public void envoyerMailTransmissionFinance(DemandeMere demande,
                                               Utilisateur transmetteur,
                                               String commentaire,
                                               List<Utilisateur> financeUsers) {
        if (financeUsers == null || financeUsers.isEmpty()) return;

        String dateTransmission = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        for (Utilisateur destinataire : financeUsers) {
            if (destinataire.getMail() == null || destinataire.getMail().isBlank()) continue;

            System.out.println(">>> Mail transmission finance");
            System.out.println("    to           : " + destinataire.getMail());
            System.out.println("    demande      : " + demande.getId());
            System.out.println("    transmetteur : " + transmetteur.getNom());

            Map<String, Object> props = new HashMap<>();
            props.put("id",               demande.getId());
            props.put("demandeur",        demande.getDemandeur());
            props.put("transmetteur",     transmetteur);
            props.put("destinataire",     destinataire);
            props.put("dateTransmission", dateTransmission);
            props.put("montant",          demande.getTotalEstime() != null
                                              ? demande.getTotalEstime()
                                              : demande.getTotalPrix());
            props.put("commentaire",      commentaire);
            props.put("lienFiche",        baseUrl + "/demande/fiche/" + demande.getId());

            this.sendEmail(new Mail(
                    "paiementTransmisFinance",
                    destinataire.getMail(),
                    "[AFG Bank - Demande Achat - Paiement Direct] - Nouvelle demande transmise à la finance",
                    props
            ));
        }
    }

    /**
     * Notifie tous les utilisateurs Finance qu'un paiement direct a été effectué.
     *
     * @param demande      la demande concernée
     * @param payeur       l'utilisateur qui a enregistré le paiement
     * @param commentaire  commentaire éventuel de paiement
     * @param financeUsers liste des utilisateurs ayant ROLE_FINANCE
     */
    public void envoyerMailPaiementEffectue(DemandeMere demande,
                                            Utilisateur payeur,
                                            String commentaire,
                                            List<Utilisateur> financeUsers) {
        if (financeUsers == null || financeUsers.isEmpty()) return;

        String datePaiement = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        for (Utilisateur destinataire : financeUsers) {
            if (destinataire.getMail() == null || destinataire.getMail().isBlank()) continue;

            System.out.println(">>> Mail paiement effectué");
            System.out.println("    to       : " + destinataire.getMail());
            System.out.println("    demande  : " + demande.getId());
            System.out.println("    payeur   : " + payeur.getNom());

            Map<String, Object> props = new HashMap<>();
            props.put("id",           demande.getId());
            props.put("demandeur",    demande.getDemandeur());
            props.put("payeur",       payeur);
            props.put("destinataire", destinataire);
            props.put("datePaiement", datePaiement);
            props.put("montant",      demande.getTotalEstime() != null
                                          ? demande.getTotalEstime()
                                          : demande.getTotalPrix());
            props.put("commentaire",  commentaire);
            props.put("lienFiche",    baseUrl + "/demande/fiche/" + demande.getId());

            this.sendEmail(new Mail(
                    "paiementEffectue",
                    destinataire.getMail(),
                    "[AFG Bank - Demande Achat - Paiement Direct] - Paiement effectué",
                    props
            ));
        }
    }
}
