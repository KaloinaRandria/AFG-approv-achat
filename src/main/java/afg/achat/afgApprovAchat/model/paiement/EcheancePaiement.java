package afg.achat.afgApprovAchat.model.paiement;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EcheancePaiement {
    int id;
    BonCommandeMere bonCommandeMere;
    int numeroTranche;
    double montant;
    int pourcentage;
    LocalDate dateEcheance;
    String statut;
    LocalDate datePaiementReel;
}
