package afg.achat.afgApprovAchat.model;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import lombok.AllArgsConstructor;
import lombok.Getter;

// LigneRestanteDTO.java
@Getter
@AllArgsConstructor
public class LigneRestanteDTO {
    private DemandeFille demandeFille;
    private double totalSorti;
    private double restant;
    private double stockDisponible;
}
