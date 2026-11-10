package afg.achat.afgApprovAchat.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.util.Map;

import static org.mockito.Mockito.*;

class EmailSenderServiceTest {

    @Test
    void mailsDesactivesRienNEstEnvoye() {
        JavaMailSender sender = mock(JavaMailSender.class);
        TemplateEngine templates = mock(TemplateEngine.class);
        EmailSenderService service = new EmailSenderService(sender, templates);
        ReflectionTestUtils.setField(service, "mailEnabled", false);

        service.sendEmail(new Mail("demandeSaved", "x@test.local", "Sujet", Map.of()));

        verifyNoInteractions(sender, templates);
    }
}
