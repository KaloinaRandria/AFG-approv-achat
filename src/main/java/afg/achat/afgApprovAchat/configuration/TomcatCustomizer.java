package afg.achat.afgApprovAchat.configuration;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class TomcatCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers((Connector connector) -> {
            connector.setProperty("maxParameterCount", "1000"); // chaque fichier est un param
            connector.setProperty("maxSwallowSize", "-1");     // accepte les gros fichiers
        });
    }
}