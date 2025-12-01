package afg.achat.afgApprovAchat.service.utilisateur;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.utilisateur.UtilisateurRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class UtilisateurServiceTest {
    @Mock
    private UtilisateurRepo utilisateurRepo;
    @InjectMocks
    private UtilisateurService utilisateurService;

    @Test
    void testGetUtilisateurByMail() {
        // 🧪 1. Préparer un faux utilisateur
        Utilisateur fakeUser = new Utilisateur();
        fakeUser.setMail("test@example.com");

        // 🧪 2. Simuler le comportement du repository
        Mockito.when(utilisateurRepo.findByMail("test@example.com"))
                .thenReturn(fakeUser);

        // 🧪 3. Appeler la méthode du service
        Utilisateur result = utilisateurService.getUtilisateurByMail("test@example.com");

        // 🧪 4. Vérifications (assertions)
        assertNotNull(result);
        assertEquals("test@example.com", result.getMail());

        // 🧪 5. Vérifier que la méthode du repo est appelée une seule fois
        Mockito.verify(utilisateurRepo, Mockito.times(1))
                .findByMail("test@example.com");

        System.out.println("Resultat du test : " + result.getMail());
    }

}
