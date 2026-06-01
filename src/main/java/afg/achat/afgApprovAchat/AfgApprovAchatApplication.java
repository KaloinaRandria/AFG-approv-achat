package afg.achat.afgApprovAchat;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import java.util.TimeZone;

@SpringBootApplication
public class AfgApprovAchatApplication extends SpringBootServletInitializer {

	 @Override
	    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
	        return application.sources(AfgApprovAchatApplication.class);
	    }

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Indian/Antananarivo"));
	}

    public static void main(String[] args) {
        SpringApplication.run(AfgApprovAchatApplication.class, args);
    }

}
