package afg.achat.afgApprovAchat.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class PaginationUrlTest {

    @Test
    void retireLaPageEtGardeLesFiltresEncodes() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/AFG-approv-achat/bon-sortie/list");
        request.setQueryString("page=3&size=50&demandeur=Rakoto%20Jean&statut=CONFIRME");

        assertEquals("/AFG-approv-achat/bon-sortie/list?size=50&demandeur=Rakoto%20Jean&statut=CONFIRME",
                PaginationUrl.sansPage(request));
    }

    @Test
    void sansParametreAdresseSeule() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/AFG-approv-achat/bon-commande/list");
        assertEquals("/AFG-approv-achat/bon-commande/list", PaginationUrl.sansPage(request));

        request.setQueryString("page=2");
        assertEquals("/AFG-approv-achat/bon-commande/list", PaginationUrl.sansPage(request));
    }
}
