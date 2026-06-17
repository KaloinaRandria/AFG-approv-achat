package afg.achat.afgApprovAchat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@PreAuthorize("hasAnyRole('ADMIN', 'MOYENS_GENERAUX')")
@Controller
@RequestMapping("/bon-commande")
@RequiredArgsConstructor
public class BonCommandeController {
}
