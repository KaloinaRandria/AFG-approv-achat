package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviseDemandeServiceTest {

    @Mock DemandeFilleRepo demandeFilleRepo;
    @Mock DemandeMereService demandeMereService;
    @InjectMocks DeviseDemandeService service;

    private DemandeMere demande;
    private DemandeFille achat;
    private DemandeFille stock;
    private DemandeFille refusee;

    private static Devise devise(String code, double cours) {
        Devise d = new Devise();
        d.setAcronyme(code);
        d.setCoursAriary(String.valueOf(cours));
        return d;
    }

    private DemandeFille ligne(TypeApprovisionnement type, double prix) {
        DemandeFille l = new DemandeFille();
        l.setDemandeMere(demande);
        l.setQuantite("2");
        l.setTypeApprovisionnement(type);
        l.setPrixUnitaire(prix);
        return l;
    }

    @BeforeEach
    void setUp() {
        demande = new DemandeMere();
        demande.setStatut(StatutDemande.VALIDATION_N1);
        demande.appliquerDevise(devise("MGA", 1), LocalDateTime.now());
        achat = ligne(TypeApprovisionnement.ACHAT, 81000);
        stock = ligne(TypeApprovisionnement.STOCK, 10000);
        refusee = ligne(TypeApprovisionnement.ACHAT, 3000);
        refusee.setStatut(StatutDemande.REFUSE);
        when(demandeFilleRepo.findDemandeFilleByDemandeMere(demande)).thenReturn(List.of(achat, stock, refusee));
    }

    @Test
    void passageEnEurosVideLesPrixAAcheterEtConvertitLeStock() {
        int aRessaisir = service.changerDevise(demande, devise("EUR", 5000));

        assertEquals(1, aRessaisir);
        assertEquals("EUR", demande.getCodeDevise());
        assertEquals(5000.0, demande.getTauxChange());
        assertNull(achat.getPrixUnitaire());
        assertNull(achat.getMontantEstime());
        assertEquals(2.0, stock.getPrixUnitaire(), 1e-9);    // 10 000 MGA / 5 000
        assertEquals(3000.0, refusee.getPrixUnitaire());     // ligne refusée intacte
        verify(demandeMereService).recalculerTotal(demande);
    }

    @Test
    void lesLignesStockSontConverteiesDUneDeviseEtrangereAUneAutre() {
        service.changerDevise(demande, devise("EUR", 5000));
        service.changerDevise(demande, devise("USD", 4000));

        // 2 EUR = 10 000 MGA = 2,5 USD
        assertEquals(2.5, stock.getPrixUnitaire(), 1e-9);
    }

    @Test
    void memeDeviseRienNeChange() {
        assertEquals(0, service.changerDevise(demande, devise("MGA", 1)));
        assertEquals(81000.0, achat.getPrixUnitaire());
        verify(demandeFilleRepo, never()).save(any());
    }

    @Test
    void horsEtapeMgRefuse() {
        demande.setStatut(StatutDemande.VALIDE);
        assertThrows(IllegalArgumentException.class, () -> service.changerDevise(demande, devise("EUR", 5000)));
        assertEquals(81000.0, achat.getPrixUnitaire());
    }
}
