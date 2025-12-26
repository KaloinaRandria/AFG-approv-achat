package afg.achat.afgApprovAchat.configuration;

import afg.achat.afgApprovAchat.service.util.FormatNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.springframework.beans.factory.annotation.Autowired;


@Configuration
public class ThymeleafConfig {

    @Autowired
    private ThymeleafViewResolver thymeleafViewResolver;

    @PostConstruct
    public void configureThymeleaf() {
        thymeleafViewResolver.addStaticVariable("FormatNumber", new FormatNumber());
    }
}
