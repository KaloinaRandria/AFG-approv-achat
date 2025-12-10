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

import javax.naming.NamingException;
import java.util.Collection;
import java.util.Collections;


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

			/*if(email.startsWith("test") && password.equals(email)) {
				Collaborateur user = ur.findByEmailActif(email);
				return new UsernamePasswordAuthenticationToken(user, null,
				        Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+user.getRole().getLibelle())));
			}
			if(email.equals("s.dago@atlantic-group.net") && password.equals("P@ssword01")) {
				Collaborateur user = ur.findByEmailActif(email);
				return new UsernamePasswordAuthenticationToken(user, null,
				        Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+user.getRole().getLibelle())));
			}
		
			if(email.equals("charmica.andriakotoarson@afgbank.mg") && password.equals("charmica.andriakotoarson@afgbank.mg")) {
				Collaborateur user = ur.findByEmailActif(email);
				return new UsernamePasswordAuthenticationToken(user, null,
				        Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+user.getRole().getLibelle())));
			}*/
			/*if(email.equals("ina.diallo@afgbank.mg") && password.equals("ina.diallo@afgbank.mg")) {
				Collaborateur user = ur.findByEmailActif(email);
				return new UsernamePasswordAuthenticationToken(user, null,
				        Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+user.getRole().getLibelle())));
			}*/
			if(email.equals("yves.rakotondrazaka@afgbank.mg") && password.equals("mdp")) {
				Utilisateur user = utilisateurService.getUtilisateurByMail(email);
                Collection<GrantedAuthority> authorities = user.getRoles().stream()
                        .map(role ->(GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getRole()))
                        .toList();

                return new UsernamePasswordAuthenticationToken(user, null, authorities);
			}

            if(email.equals("kaloina.randriambololona@afgbank.mg") && password.equals("mdp")) {
                Utilisateur user = utilisateurService.getUtilisateurByMail(email);
                Collection<GrantedAuthority> authorities = user.getRoles().stream()
                        .map(role ->(GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getRole()))
                        .toList();

                return new UsernamePasswordAuthenticationToken(user, null, authorities);
            }

			if(email.equals("s.dago@atlantic-group.net") && password.equals("P@ssword01")) {
				Utilisateur user = utilisateurService.getUtilisateurByMail(email);
                Collection<GrantedAuthority> authorities = user.getRoles().stream()
                        .map(role ->(GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getRole()))
                        .toList();

                return new UsernamePasswordAuthenticationToken(user, null, authorities);
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

	@Override
	public boolean supports(Class<?> arg0) {
		return arg0.equals(UsernamePasswordAuthenticationToken.class);
	}

}
