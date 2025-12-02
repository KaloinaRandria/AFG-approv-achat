package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.service.stock.StockAlerteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/message")
public class MessageController {
    @Autowired
    StockAlerteService stockAlerteService;

    @GetMapping("/alertes-stock")
    public String afficherAlerte(Model model) {
        model.addAttribute("alertes", stockAlerteService.getAlertes());
        return "alerte/message";
    }
}
