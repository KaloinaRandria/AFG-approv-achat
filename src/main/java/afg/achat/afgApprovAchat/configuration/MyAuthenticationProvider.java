package afg.achat.afgApprovAchat.configuration;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import javax.naming.NamingException;
import java.util.Collection;
import java.util.Collections;


@Slf4j
@Component
public class MyAuthenticationProvider implements AuthenticationProvider{

	@Autowired
	private ActiveDirectory activeDir ; 

    @Autowired
    private UtilisateurService utilisateurService;
	
	@Override
	public Authentication authenticate(Authentication arg0) throws AuthenticationException {
		String email = arg0.getName().trim() ;
		String password = arg0.getCredentials().toString().trim() ;
		try {

            // Connexion de test (profil dev uniquement) : comptes listés dans
            // app.auth.test-login.comptes, avec le mot de passe app.auth.test-login.password.
            if (estConnexionDeTest(email, password)) {
                Utilisateur user = utilisateurService.getUtilisateurByMail(email);
                if (user == null) {
                    throw new BadCredentialsException("Utilisateur non autorisé");
                }
                log.info("Connexion de TEST (sans Active Directory) : {}", email);
                return authentifier(user);
            }

			if(activeDir.authentify(email, password)) {
                Utilisateur user = utilisateurService.getUtilisateurByMail(email);
                if (user == null) {
                    throw new BadCredentialsException("Utilisateur non autorisé");
                }

                Collection<GrantedAuthority> authorities = user.getRoles().stream()
                        .map(role ->(GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getRole()))
                        .toList();

                return new UsernamePasswordAuthenticationToken(user, null, authorities);


            }else {
				throw new BadCredentialsException ("Email ou Mot de passe incorrect");
			}
		} catch (NamingException e) {
			throw new BadCredentialsException ("Erreur de connexion");
		}		
	}

	// ── Connexion de test ───────────────────────────────────────────────────

	/** Désactivée par défaut ; activée par le profil dev (application-dev.properties). */
	@Value("${app.auth.test-login.enabled:false}")
	private boolean testLoginEnabled;

	@Value("${app.auth.test-login.password:}")
	private String testLoginPassword;

	@Value("${app.auth.test-login.comptes:}")
	private List<String> comptesTest;

	@PostConstruct
	void signalerConnexionDeTest() {
		if (testLoginEnabled) {
			log.warn("CONNEXION DE TEST ACTIVÉE pour {} compte(s) : NE PAS UTILISER EN PRODUCTION.",
					comptesTest.size());
		}
	}

	private boolean estConnexionDeTest(String email, String password) {
		return testLoginEnabled
				&& testLoginPassword != null && !testLoginPassword.isBlank()
				&& password.equals(testLoginPassword)
				&& comptesTest.stream().anyMatch(c -> c.trim().equalsIgnoreCase(email));
	}

	private Authentication authentifier(Utilisateur user) {
		Collection<GrantedAuthority> authorities = user.getRoles().stream()
				.map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getRole()))
				.toList();
		return new UsernamePasswordAuthenticationToken(user, null, authorities);
	}

	@Override
	public boolean supports(Class<?> arg0) {
		return arg0.equals(UsernamePasswordAuthenticationToken.class);
	}

}
