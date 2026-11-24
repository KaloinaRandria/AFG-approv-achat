package afg.achat.afgApprovAchat.service.demande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeFille.TypeApprovisionnement;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
import afg.achat.afgApprovAchat.service.stock.LotStockService;
import afg.achat.afgApprovAchat.service.stock.ReservationStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovisionnementLigneServiceTest {

    @Mock DemandeFilleRepo demandeFilleRepo;
    @Mock DemandeMereService demandeMereService;
    @Mock LotStockService lotStockService;
    @Mock ReservationStockService reservationStockService;
    @Mock BonSortieFilleRepo bonSortieFilleRepo;
    @InjectMocks ApprovisionnementLigneService service;

    private DemandeMere demande;
    private DemandeFille ligne;

    @BeforeEach
    void setUp() {
        demande = new DemandeMere();
        demande.setStatut(StatutDemande.VALIDATION_N1);

        Article article = new Article();
        article.setCodeArticle("ART1");

        ligne = new DemandeFille();
        ligne.setId(1);
        ligne.setDemandeMere(demande);
        ligne.setArticle(article);
        ligne.setQuantite("10");
        ligne.setPrixUnitaire(500.0);
        ligne.setTypeApprovisionnement(TypeApprovisionnement.ACHAT);

        when(demandeFilleRepo.findById(1)).thenReturn(Optional.of(ligne));
        when(demandeFilleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationStockService.stockLibre(eq("ART1"), any())).thenReturn(8.0);
        when(lotStockService.getPrixFifo(eq("ART1"), anyDouble())).thenReturn(300.0);
    }

    @Test
    void scissionPartielleGardeLAchatEtCreeUneLigneStock() {
        DemandeFille stock = service.scinder(1, 4, 0);

        assertEquals(6.0, ligne.getQuantite());
        assertEquals(TypeApprovisionnement.ACHAT, ligne.getTypeApprovisionnement());
        assertEquals(3000.0, ligne.getMontantEstime());
        assertNotSame(ligne, stock);
        assertEquals(4.0, stock.getQuantite());
        assertEquals(TypeApprovisionnement.STOCK, stock.getTypeApprovisionnement());
        assertEquals(300.0, stock.getPrixUnitaire());
        verify(demandeMereService).recalculerTotal(demande);
    }

    @Test
    void scissionTotaleAvecArrondiPasseLaLigneEnStock() {
        when(reservationStockService.stockLibre(eq("ART1"), any())).thenReturn(20.0);

        DemandeFille stock = service.scinder(1, 9.9999, 0);

        assertSame(ligne, stock);
        assertEquals(TypeApprovisionnement.STOCK, ligne.getTypeApprovisionnement());
        verify(demandeFilleRepo, times(1)).save(any());
    }

    @Test
    void scissionAuDelaDuStockLibreRefuseeSansEcriture() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.scinder(1, 9, 0));
        assertTrue(e.getMessage().startsWith("Stock insuffisant"));
        verify(demandeFilleRepo, never()).save(any());
    }

    @Test
    void demandeEnDeviseEtrangereLePrixFifoEstConverti() {
        afg.achat.afgApprovAchat.model.util.Devise eur = new afg.achat.afgApprovAchat.model.util.Devise();
        eur.setAcronyme("EUR");
        eur.setCoursAriary("5000");
        demande.appliquerDevise(eur, java.time.LocalDateTime.now());

        // 300 MGA (FIFO) = 0,06 EUR
        assertEquals(0.06, service.scinder(1, 4, 0).getPrixUnitaire(), 1e-9);
    }

    @Test
    void prixDeRepliSiLePrixFifoEchoue() {
        when(lotStockService.getPrixFifo(eq("ART1"), anyDouble())).thenThrow(new IllegalStateException("x"));
        assertEquals(450.0, service.scinder(1, 4, 450).getPrixUnitaire());
    }

    @Test
    void remettreEnAchatLibereLaLigneStock() {
        ligne.setTypeApprovisionnement(TypeApprovisionnement.STOCK);
        demande.setStatut(StatutDemande.VALIDE);
        when(bonSortieFilleRepo.sumQuantiteSortieByDemandeFille(ligne)).thenReturn(3.0);

        service.remettreEnAchat(1);

        assertEquals(TypeApprovisionnement.ACHAT, ligne.getTypeApprovisionnement());
        assertEquals(10.0, ligne.getQuantite()); // le reste à acheter (7) se calcule : demandé − sorti
        verify(demandeFilleRepo).save(ligne);
    }

    @Test
    void ligneEntierementSortieNePeutPasEtreRemiseALAchat() {
        ligne.setTypeApprovisionnement(TypeApprovisionnement.STOCK);
        when(bonSortieFilleRepo.sumQuantiteSortieByDemandeFille(ligne)).thenReturn(10.0);

        assertThrows(IllegalArgumentException.class, () -> service.remettreEnAchat(1));
        verify(demandeFilleRepo, never()).save(any());
    }

    @Test
    void ligneRefuseeNonModifiable() {
        ligne.setStatut(StatutDemande.REFUSE);
        assertThrows(IllegalArgumentException.class, () -> service.scinder(1, 2, 0));
        verify(demandeFilleRepo, never()).save(any());
    }
}
