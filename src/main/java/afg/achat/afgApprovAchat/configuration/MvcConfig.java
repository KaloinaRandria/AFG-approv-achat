package afg.achat.afgApprovAchat.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableWebSecurity
@Configuration
public class MvcConfig implements WebMvcConfigurer{

	
	@Bean
	public SCryptPasswordEncoder passwordEncoder () {
		SCryptPasswordEncoder deleg = new SCryptPasswordEncoder(16384,8,1,32,64);
		return deleg ; 
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		WebMvcConfigurer.super.addViewControllers(registry);
		registry.addViewController("/login").setViewName("login");
		registry.addViewController("/accessDenied").setViewName("accessDenied");
	}
	
	
}
