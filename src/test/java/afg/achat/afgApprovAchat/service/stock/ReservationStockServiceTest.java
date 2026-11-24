package afg.achat.afgApprovAchat.service.stock;

import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationStockServiceTest {

    @Mock LotStockService lotStockService;
    @Mock DemandeFilleRepo demandeFilleRepo;
    @InjectMocks ReservationStockService service;

    @Test
    void stockLibreDeduitLeReserveNonSorti() {
        when(lotStockService.getStockDisponible("ART1")).thenReturn(10.0);
        // 6 promis par des lignes STOCK, dont 2 déjà sortis : 4 réservés
        when(demandeFilleRepo.sommeQuantiteLignesStock(eq("ART1"), anyInt(), any())).thenReturn(6.0);
        when(demandeFilleRepo.sommeSortiesLignesStock(eq("ART1"), anyInt(), any())).thenReturn(2.0);

        assertEquals(4.0, service.quantiteReservee("ART1", List.of(7)));
        assertEquals(6.0, service.stockLibre("ART1", List.of(7)));
    }

    @Test
    void stockLibreJamaisNegatif() {
        when(lotStockService.getStockDisponible("ART1")).thenReturn(1.0);
        when(demandeFilleRepo.sommeQuantiteLignesStock(eq("ART1"), anyInt(), any())).thenReturn(5.0);
        when(demandeFilleRepo.sommeSortiesLignesStock(eq("ART1"), anyInt(), any())).thenReturn(0.0);

        assertEquals(0.0, service.stockLibre("ART1", List.of()));
    }

    @Test
    void sansExclusionUneListeSentinelleEstPassee() {
        // NOT IN () serait du SQL invalide
        service.quantiteReservee("ART1", List.of());
        verify(demandeFilleRepo).sommeQuantiteLignesStock(eq("ART1"), anyInt(), eq(List.of(-1)));
    }
}
