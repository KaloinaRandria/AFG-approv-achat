package afg.achat.afgApprovAchat.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adresse de la page courante sans le paramètre {@code page}, pour le fragment de pagination
 * ({@code fragment/pagination}) : tous les filtres de la liste sont conservés d'une page à l'autre.
 */
public final class PaginationUrl {

    private PaginationUrl() {
    }

    /** Chemin relatif (avec le préfixe de l'application) et paramètres déjà encodés, sans {@code page}. */
    public static String sansPage(HttpServletRequest request) {
        return UriComponentsBuilder.fromPath(request.getRequestURI())
                .query(request.getQueryString())
                .replaceQueryParam("page")
                .build()
                .toUriString();
    }
}
