package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.service.stock.ReservationStockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SortieAFaireServiceTest {

    @Mock DemandeFilleRepo demandeFilleRepo;
    @Mock BonSortieFilleRepo bonSortieFilleRepo;
    @Mock ReservationStockService reservationStockService;
    @InjectMocks SortieAFaireService service;

    private static DemandeFille ligne(int id, DemandeMere dm, String code, double quantite) {
        Article a = new Article();
        a.setCodeArticle(code);
        DemandeFille df = new DemandeFille();
        df.setId(id);
        df.setDemandeMere(dm);
        df.setArticle(a);
        df.setQuantite(String.valueOf(quantite));
        return df;
    }

    @Test
    void lesDemandesAvecDuStockPassentEnPremier() {
        DemandeMere attente = new DemandeMere();
        DemandeMere prete = new DemandeMere();
        DemandeFille clavier = ligne(1, attente, "CLAVIER", 5);   // plus ancienne, sans stock
        DemandeFille ecran = ligne(2, prete, "ECRAN", 4);
        DemandeFille souris = ligne(3, prete, "SOURIS", 2);
        when(demandeFilleRepo.lignesASortir(StatutDemande.VALIDE, StatutDemande.REFUSE))
                .thenReturn(List.of(clavier, ecran, souris));
        when(bonSortieFilleRepo.sumQuantiteSortieByDemandeFille(ecran)).thenReturn(1.0);
        when(reservationStockService.stockLibre(eq("CLAVIER"), any())).thenReturn(0.0);
        when(reservationStockService.stockLibre(eq("ECRAN"), any())).thenReturn(10.0);
        when(reservationStockService.stockLibre(eq("SOURIS"), any())).thenReturn(0.0);

        List<SortieAFaireService.DemandeASortir> demandes = service.demandes();

        assertEquals(2, demandes.size());
        assertSame(prete, demandes.get(0).demande());
        assertTrue(demandes.get(0).prete());
        assertEquals(1, demandes.get(0).articlesDisponibles());
        assertEquals(3.0, demandes.get(0).lignes().get(0).reste());      // 4 demandés - 1 sorti
        assertEquals(3.0, demandes.get(0).lignes().get(0).sortable());   // borné par le reste
        assertFalse(demandes.get(1).prete());
        assertEquals(1, service.nombrePretes());
    }

    @Test
    void lesReservationsDeLaDemandeNeComptentPasContreElle() {
        DemandeMere dm = new DemandeMere();
        DemandeFille a = ligne(7, dm, "ART", 3);
        DemandeFille b = ligne(8, dm, "ART", 2);
        when(demandeFilleRepo.lignesASortir(anyInt(), anyInt())).thenReturn(List.of(a, b));
        when(reservationStockService.stockLibre(eq("ART"), any())).thenReturn(1.0);

        service.demandes();

        verify(reservationStockService, times(2)).stockLibre("ART", List.of(7, 8));
    }

    @Test
    void stockPartielSortableBorneParLeStock() {
        SortieAFaireService.LigneASortir l = new SortieAFaireService.LigneASortir(new DemandeFille(), 5, 2);
        assertEquals(2.0, l.sortable());
        assertTrue(l.disponible());
        assertFalse(new SortieAFaireService.LigneASortir(new DemandeFille(), 5, 0.0005).disponible());
    }
}
