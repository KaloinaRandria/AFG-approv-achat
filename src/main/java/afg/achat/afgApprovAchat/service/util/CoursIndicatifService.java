package afg.achat.afgApprovAchat.service.util;

import afg.achat.afgApprovAchat.model.util.Devise;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Cours <em>indicatif</em> d'une devise en ariary, lu sur une API publique de taux de change
 * (par défaut open.er-api.com, gratuite et sans clé, mise à jour une fois par jour).
 * <p>
 * Ce cours sert uniquement à pré-remplir la saisie : il n'est jamais enregistré sans
 * qu'un utilisateur le valide. Ce n'est pas le cours officiel de la Banque centrale.
 * <p>
 * Les taux sont lus avec le dollar pour base, puis 1 devise = taux(MGA) / taux(devise) MGA :
 * une base MGA ne donne que 3 chiffres significatifs (1 MGA = 0,000201 EUR).
 */
@Service
public class CoursIndicatifService {

    /** Cours indicatif d'une devise : 1 {@code devise} = {@code coursAriary} MGA. */
    public record CoursIndicatif(String devise, double coursAriary, LocalDateTime dateSource, String source) {}

    private final String url;
    private final Duration timeout;
    private final Duration dureeCache;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile JsonNode tauxEnCache;
    private volatile Instant dateCache = Instant.EPOCH;

    public CoursIndicatifService(
            @Value("${devise.cours-indicatif.url:https://open.er-api.com/v6/latest/USD}") String url,
            @Value("${devise.cours-indicatif.timeout-secondes:8}") long timeoutSecondes,
            @Value("${devise.cours-indicatif.cache-minutes:60}") long cacheMinutes,
            @Value("${devise.cours-indicatif.proxy-host:}") String proxyHost,
            @Value("${devise.cours-indicatif.proxy-port:0}") int proxyPort) {
        this.url = url;
        this.timeout = Duration.ofSeconds(timeoutSecondes);
        this.dureeCache = Duration.ofMinutes(cacheMinutes);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
        } else {
            // Proxy de la JVM (-Dhttps.proxyHost / -Dhttps.proxyPort) s'il est défini
            builder.proxy(ProxySelector.getDefault());
        }
        this.client = builder.build();
    }

    /**
     * @throws IllegalArgumentException si la devise n'est pas publiée par la source
     * @throws IllegalStateException    si la source est injoignable ou répond mal
     */
    public CoursIndicatif coursIndicatif(String acronyme) {
        String code = acronyme == null ? "" : acronyme.trim().toUpperCase();
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Code de devise invalide.");
        }
        return calculer(code, lireTaux());
    }

    /** Calcul à partir de la réponse JSON de la source (format open.er-api.com). */
    static CoursIndicatif calculer(String code, JsonNode reponse) {
        JsonNode rates = reponse.path("rates");
        double tauxMga = rates.path(Devise.CODE_REFERENCE).asDouble(0);
        double tauxDevise = rates.path(code).asDouble(0);
        if (!(tauxMga > 0)) {
            throw new IllegalStateException("La source ne publie pas le cours de l'ariary.");
        }
        if (!(tauxDevise > 0)) {
            throw new IllegalArgumentException("La source ne publie pas de cours pour " + code + ".");
        }
        // 4 décimales : les petites devises (XOF ≈ 7,6675 MGA) perdraient trop à 2 décimales
        double cours = Math.round(tauxMga / tauxDevise * 10_000.0) / 10_000.0;
        LocalDateTime date = null;
        long unix = reponse.path("time_last_update_unix").asLong(0);
        if (unix > 0) {
            date = LocalDateTime.ofInstant(Instant.ofEpochSecond(unix), ZoneId.systemDefault());
        }
        String source = reponse.path("provider").asText("");
        return new CoursIndicatif(code, cours, date, source.isBlank() ? "open.er-api.com" : source);
    }

    private JsonNode lireTaux() {
        JsonNode cache = tauxEnCache;
        if (cache != null && Instant.now().isBefore(dateCache.plus(dureeCache))) {
            return cache;
        }
        synchronized (this) {
            if (tauxEnCache != null && Instant.now().isBefore(dateCache.plus(dureeCache))) {
                return tauxEnCache;
            }
            try {
                HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .GET().build();
                HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
                if (reponse.statusCode() != 200) {
                    throw new IllegalStateException("La source de cours a répondu " + reponse.statusCode() + ".");
                }
                JsonNode json = mapper.readTree(reponse.body());
                if (!"success".equalsIgnoreCase(json.path("result").asText("success"))) {
                    throw new IllegalStateException("La source de cours a renvoyé une erreur.");
                }
                tauxEnCache = json;
                dateCache = Instant.now();
                return json;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Récupération du cours interrompue.");
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Source de cours injoignable (accès Internet ou proxy ?).", e);
            }
        }
    }
}
