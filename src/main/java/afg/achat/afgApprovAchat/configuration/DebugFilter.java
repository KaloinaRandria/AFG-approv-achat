//package afg.achat.afgApprovAchat.configuration;
//
//import jakarta.servlet.*;
//import jakarta.servlet.http.HttpServletRequest;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//
//@Component
//@Order(1) // s'exécute en premier
//public class DebugFilter implements Filter {
//
//    @Override
//    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
//            throws IOException, ServletException {
//
//        HttpServletRequest req = (HttpServletRequest) request;
//        System.out.println("=== INCOMING URL: " + req.getRequestURL()
//                + (req.getQueryString() != null ? "?" + req.getQueryString() : ""));
//
//        chain.doFilter(request, response);
//    }
//}