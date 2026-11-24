package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MouvementStockServiceTest {

    @Mock LotStockService lotStockService;
    @Mock StockMereService stockMereService;
    @InjectMocks MouvementStockService service;

    private final StockMere stockMere = new StockMere();

    private static Article article() {
        Article a = new Article();
        a.setCodeArticle("ART1");
        return a;
    }

    @Test
    void entreeCreeUnLotEtUnMouvementDEntree() {
        Article a = article();
        LocalDateTime date = LocalDateTime.of(2026, 1, 15, 0, 0);

        service.entrer(stockMere, a, 10, 1500, null, date);

        verify(lotStockService).creerLot(a, null, 10, 1500, date);
        verify(stockMereService).addEntree(stockMere, a, 10);
    }

    @Test
    void entreeManuelleCreeSonEnTete() {
        Article a = article();
        service.entreeManuelle(a, 4, 200);

        verify(stockMereService).insertStockMere(any(StockMere.class));
        verify(lotStockService).creerLot(eq(a), isNull(), eq(4.0), eq(200.0), isNull());
        verify(stockMereService).addEntree(any(StockMere.class), eq(a), eq(4.0));
    }

    @Test
    void entreeInvalideRienNEstEcrit() {
        Article a = article();
        assertThrows(IllegalArgumentException.class, () -> service.entrer(stockMere, a, 0, 100, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.entrer(stockMere, a, 5, -1, null, null));
        verifyNoInteractions(lotStockService, stockMereService);
    }

    @Test
    void sortieConsommeLesLotsEtEnregistreLaSortie() {
        Article a = article();
        when(lotStockService.getStockDisponible("ART1")).thenReturn(8.0);

        service.sortir(stockMere, a, 5);

        verify(lotStockService).consommerStock("ART1", 5);
        verify(stockMereService).addSortie(stockMere, a, 5);
    }

    @Test
    void sortieAuDelaDesLotsRefuseeSansEcriture() {
        Article a = article();
        when(lotStockService.getStockDisponible("ART1")).thenReturn(2.0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.sortir(stockMere, a, 5));

        assertTrue(e.getMessage().startsWith("Stock insuffisant"));
        verify(lotStockService, never()).consommerStock(any(), anyDouble());
        verify(stockMereService, never()).addSortie(any(), any(), anyDouble());
    }
}
