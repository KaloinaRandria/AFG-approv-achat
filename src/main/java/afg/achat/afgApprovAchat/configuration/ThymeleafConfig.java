package afg.achat.afgApprovAchat.configuration;

import afg.achat.afgApprovAchat.service.util.FormatNumber;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
public class ThymeleafConfig {

    @Autowired
    private ThymeleafViewResolver thymeleafViewResolver;

    @Bean
    public void addFormatNumberUtility() {
        thymeleafViewResolver.addStaticVariable("FormatNumber", FormatNumber.class);
    }
}
