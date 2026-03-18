package afg.achat.afgApprovAchat.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.nio.charset.StandardCharsets;

@Configuration
public class ThymeleafEmailConfig {

	 	@Bean
	    public SpringTemplateEngine springTemplateEngine( SpringSecurityDialect sec) {
	        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
	        templateEngine.addTemplateResolver(htmlTemplateResolver());
	        templateEngine.addDialect(sec);
	        return templateEngine;
	    }
	    @Bean
	    public SpringResourceTemplateResolver htmlTemplateResolver(){
	        SpringResourceTemplateResolver emailTemplateResolver = new SpringResourceTemplateResolver();
	        emailTemplateResolver.setPrefix("classpath:/templates/");
	        emailTemplateResolver.setSuffix(".html");
	        emailTemplateResolver.setTemplateMode(TemplateMode.HTML);
	        emailTemplateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
	        emailTemplateResolver.setCacheable(false);
	        return emailTemplateResolver;
	    }
}
