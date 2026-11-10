package afg.achat.afgApprovAchat.configuration;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyAuthenticationProviderTest {

    private static final String COMPTE = "testeur@afgbank.mg";

    @Mock ActiveDirectory activeDir;
    @Mock UtilisateurService utilisateurService;
    @InjectMocks MyAuthenticationProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        Utilisateur u = new Utilisateur();
        u.setMail(COMPTE);
        u.setRoles(new java.util.HashSet<>());
        when(utilisateurService.getUtilisateurByMail(COMPTE)).thenReturn(u);
        when(activeDir.authentify(anyString(), anyString())).thenReturn(false);
        ReflectionTestUtils.setField(provider, "testLoginPassword", "mdp");
        ReflectionTestUtils.setField(provider, "comptesTest", List.of(COMPTE));
    }

    private Authentication login(String email, String password) {
        return provider.authenticate(new UsernamePasswordAuthenticationToken(email, password));
    }

    @Test
    void parDefautLaConnexionDeTestEstRefusee() {
        ReflectionTestUtils.setField(provider, "testLoginEnabled", false);
        assertThrows(BadCredentialsException.class, () -> login(COMPTE, "mdp"));
    }

    @Test
    void profilDevAccepteLesComptesDeTest() {
        ReflectionTestUtils.setField(provider, "testLoginEnabled", true);
        assertNotNull(login(COMPTE, "mdp"));
    }

    @Test
    void profilDevRefuseUnCompteNonListe() {
        ReflectionTestUtils.setField(provider, "testLoginEnabled", true);
        assertThrows(BadCredentialsException.class, () -> login("autre@afgbank.mg", "mdp"));
    }

    @Test
    void profilDevRefuseUnMauvaisMotDePasse() {
        ReflectionTestUtils.setField(provider, "testLoginEnabled", true);
        assertThrows(BadCredentialsException.class, () -> login(COMPTE, "faux"));
    }

    @Test
    void activeDirectoryResteLaVoieNormale() throws Exception {
        ReflectionTestUtils.setField(provider, "testLoginEnabled", false);
        when(activeDir.authentify(COMPTE, "vraiMdp")).thenReturn(true);
        assertNotNull(login(COMPTE, "vraiMdp"));
    }
}
