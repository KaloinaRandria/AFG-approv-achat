package afg.achat.afgApprovAchat.configuration;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler{

	@Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {
		HttpSession session = request.getSession(true);
		String roleCurrentUser = authentication.getAuthorities().toString();
        String redirectURL = request.getContextPath();
        System.out.println("User Role : " + roleCurrentUser);
//        if(roleCurrentUser.equals("ROLE_RH")) {
//        	redirectURL = "rh";
//        }
//        if(roleCurrentUser.equals("ROLE_Manager") || roleCurrentUser.equals("ROLE_Directeur")) {
//        	redirectURL = "manager";
//        }
//        if (roleCurrentUser.equals("ROLE_ADMIN")) {
//            redirectURL = "admin";
//        }
       // System.out.println(redirectURL);
       /* if(roleCurrentUser.equals("Admin") || roleCurrentUser.equals("Exploitation")){
       	 redirectURL = "exploitation";
        }
        if(roleCurrentUser.equals("Valideur")){
          	 redirectURL = "validation";
           }
        else if (roleCurrentUser.equals("Utilisateur")){
       	 redirectURL = "user";
        }
        else if (roleCurrentUser.equals("Habilitation")){
          	 redirectURL = "habilitation";
           }*/
       /* SavedRequest savedRequest = (SavedRequest) session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        if (savedRequest != null) {
            String lastRequestUrl = savedRequest.getRedirectUrl();
            redirectURL = lastRequestUrl;
        }*/
        response.sendRedirect(redirectURL); 
        
    }
}
