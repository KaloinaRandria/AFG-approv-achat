package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeFilleRepo;
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
class BesoinAchatServiceTest {

    @Mock DemandeFilleRepo demandeFilleRepo;
    @Mock BonSortieFilleRepo bonSortieFilleRepo;
    @Mock BonCommandeFilleRepo bonCommandeFilleRepo;

    @InjectMocks BesoinAchatService service;

    @Test
    void resteACommanderJamaisNegatif() {
        assertEquals(6, BesoinAchatService.resteACommander(10, 4, 0));
        assertEquals(0, BesoinAchatService.resteACommander(10, 4, 6));
        assertEquals(0, BesoinAchatService.resteACommander(10, 10, 6));
    }

    @Test
    void seulesLesLignesAvecUnResteSontEnAttente() {
        DemandeFille aCommander = ligne(1, 10);   // 4 sortis : reste 6
        DemandeFille couverte = ligne(2, 5);      // 5 commandés : reste 0
        DemandeFille partielle = ligne(3, 8);     // 2 sortis, 3 commandés : reste 3
        when(demandeFilleRepo.findLignesAchetables(StatutDemande.VALIDE, StatutDemande.REFUSE))
                .thenReturn(List.of(aCommander, couverte, partielle));
        when(bonSortieFilleRepo.quantitesSortiesParLigne(anyList()))
                .thenReturn(List.of(new Object[]{1, 4.0}, new Object[]{3, 2.0}));
        when(bonCommandeFilleRepo.quantitesCommandeesParLigne(anyList()))
                .thenReturn(List.of(new Object[]{2, 5.0}, new Object[]{3, 3.0}));

        List<BesoinAchatService.BesoinAchat> enAttente = service.lignesEnAttente();

        assertEquals(2, enAttente.size());
        assertSame(aCommander, enAttente.get(0).getLigne());
        assertEquals(6, enAttente.get(0).getResteACommander());
        assertSame(partielle, enAttente.get(1).getLigne());
        assertEquals(3, enAttente.get(1).getResteACommander());
    }

    @Test
    void resteDUneLigneSansCompterLeBonEnCours() {
        DemandeFille df = ligne(1, 10);
        when(bonSortieFilleRepo.sumQuantiteSortieByDemandeFille(df)).thenReturn(2.0);
        when(bonCommandeFilleRepo.quantiteCommandeeHorsBon(df, "BC9")).thenReturn(3.0);
        when(bonCommandeFilleRepo.quantiteCommandeeHorsBon(df, "")).thenReturn(8.0);

        assertEquals(5, service.resteACommander(df, "BC9"));
        // Nouveau bon : aucun bon n'est exclu
        assertEquals(0, service.resteACommander(df, null));
    }

    @Test
    void aucuneLigneAucuneRequete() {
        assertTrue(service.besoins(List.of()).isEmpty());
        verifyNoInteractions(bonSortieFilleRepo, bonCommandeFilleRepo);
    }

    private static DemandeFille ligne(int id, double quantite) {
        DemandeFille df = new DemandeFille();
        df.setId(id);
        df.setQuantite(String.valueOf(quantite));
        return df;
    }
}
