package afg.achat.afgApprovAchat.model.stock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StockAlerte {
    String codeArticle;
    String designation;
    double stockDisponible;
    double seuilMin;
    String typeAlerte; // "SEUIL" ou "RUPTURE"
    String udm;
}
