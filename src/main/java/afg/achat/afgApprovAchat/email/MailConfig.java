package afg.achat.afgApprovAchat.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    // Valeurs lues depuis application.properties (non sensibles)
    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Bean
    public JavaMailSenderImpl javaMailSenderImpl() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        // username / password NE SONT PLUS définis ici :
        // ils sont injectés au démarrage par MailCredentialService.initMailSender()
        // et mis à jour en direct par MailCredentialService.changerMotDePasse()

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        // Pool SMTP pour limiter les connexions simultanées
        props.put("mail.smtp.pool", "true");
        props.put("mail.smtp.pool.size", "5");

        return mailSender;
    }

    // Expose l'interface JavaMailSender pour le reste de l'application (EmailSenderService)
    // @Primary lève l'ambiguïté : JavaMailSenderImpl étant lui-même un JavaMailSender,
    // Spring voit sinon 2 candidats possibles pour le type JavaMailSender.
    @Primary
    @Bean
    public JavaMailSender javaMailSender(JavaMailSenderImpl impl) {
        return impl;
    }
}