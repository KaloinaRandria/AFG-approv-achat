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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    void pageDeLignesAvecLeDetailDesQuantites() {
        // La requête ne renvoie que les lignes avec un reste ; le service ajoute le détail
        DemandeFille aCommander = ligne(1, 10);   // 4 sortis : reste 6
        DemandeFille partielle = ligne(3, 8);     // 2 sortis, 3 commandés : reste 3
        PageRequest page = PageRequest.of(1, 2);
        when(demandeFilleRepo.rechercherLignesAAcheter(anyInt(), anyInt(), any(), any(),
                any(), any(), any(), any(), eq(page)))
                .thenReturn(new PageImpl<>(List.of(aCommander, partielle), page, 7));
        when(bonSortieFilleRepo.quantitesSortiesParLigne(anyList()))
                .thenReturn(List.of(new Object[]{1, 4.0}, new Object[]{3, 2.0}));
        when(bonCommandeFilleRepo.quantitesCommandeesParLigne(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{3, 3.0}));

        Page<BesoinAchatService.BesoinAchat> enAttente = service.lignesEnAttente(
                new BesoinAchatService.Recherche(null, null, null, null, null, null), page);

        assertEquals(7, enAttente.getTotalElements());
        assertEquals(1, enAttente.getNumber());
        assertEquals(6, enAttente.getContent().get(0).getResteACommander());
        assertEquals(3, enAttente.getContent().get(1).getResteACommander());
        assertEquals(3, enAttente.getContent().get(1).getQuantiteCommandee());
    }

    @Test
    void filtresNettoyesEtPeriodeCouvrantToutLeDernierJour() {
        PageRequest page = PageRequest.of(0, 50);
        when(demandeFilleRepo.rechercherLignesAAcheter(anyInt(), anyInt(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(Page.empty(page));

        service.lignesEnAttente(new BesoinAchatService.Recherche(" DM12 ", null, " stylo ", "",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18)), page);

        verify(demandeFilleRepo).rechercherLignesAAcheter(StatutDemande.VALIDE, StatutDemande.REFUSE,
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDate.of(2026, 11, 18).atTime(LocalTime.MAX),
                "DM12", "", "stylo", "", page);
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
