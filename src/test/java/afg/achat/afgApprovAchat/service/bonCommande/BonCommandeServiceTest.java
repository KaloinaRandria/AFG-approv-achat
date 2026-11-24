package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.EtapeValidation;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
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
class BonCommandeServiceTest {

    @Mock BonCommandeMereRepo bonCommandeMereRepo;
    @Mock BonCommandeFilleRepo bonCommandeFilleRepo;
    @Mock IdGenerator idGenerator;

    @InjectMocks BonCommandeService service;

    private final PageRequest page = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        when(bonCommandeMereRepo.rechercher(any(), anyBoolean(), any(), any(), any(),
                any(), any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(Page.empty());
    }

    @Test
    void sansFiltreTousLesStatutsEtTexteNettoye() {
        service.rechercher(new BonCommandeService.Recherche(null, false, List.of(), null, null,
                " 2026-1 ", null, "", null, " ART1 "), page);

        verify(bonCommandeMereRepo).rechercher(eq(List.of(StatutBonCommande.values())), eq(false),
                eq(List.of(EtapeValidation.values())),
                eq(LocalDateTime.of(1900, 1, 1, 0, 0)), eq(LocalDateTime.of(2999, 12, 31, 23, 59, 59)),
                eq("2026-1"), eq(""), eq(""), eq(""), eq("ART1"), eq(false), any(), eq(page));
    }

    @Test
    void statutEtPeriodeCouvrantToutLeDernierJour() {
        service.rechercher(new BonCommandeService.Recherche(StatutBonCommande.VALIDE, false, List.of(),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18), null, "Votre marque", null, "DM12", null), page);

        verify(bonCommandeMereRepo).rechercher(eq(List.of(StatutBonCommande.VALIDE)), eq(false), any(),
                eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDate.of(2026, 11, 18).atTime(LocalTime.MAX)),
                eq(""), eq("Votre marque"), eq(""), eq("DM12"), eq(""), eq(false), any(), eq(page));
    }

    @Test
    void aValiderLimiteAuxEtapesDuValideurEtIgnoreLeStatut() {
        service.rechercher(new BonCommandeService.Recherche(StatutBonCommande.BROUILLON, true,
                List.of(EtapeValidation.SG), null, null, null, null, null, null, null), page);

        verify(bonCommandeMereRepo).rechercher(eq(List.of(StatutBonCommande.EN_ATTENTE_VALIDATION)), eq(true),
                eq(List.of(EtapeValidation.SG)), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(page));
    }

    @Test
    void aRecevoirLimiteAuxBonsValidesDontLaReceptionEstOuverte() {
        service.rechercher(new BonCommandeService.Recherche(StatutBonCommande.BROUILLON, false, List.of(),
                null, null, null, null, null, null, null, true), page);

        verify(bonCommandeMereRepo).rechercher(eq(List.of(StatutBonCommande.VALIDE)), eq(false), any(),
                any(), any(), any(), any(), any(), any(), any(),
                eq(true), eq(BonCommandeService.RECEPTIONS_OUVERTES), eq(page));
    }

    @Test
    void aValiderSansEtapeValidableRienAAfficher() {
        Page<?> resultat = service.rechercher(new BonCommandeService.Recherche(null, true, List.of(),
                null, null, null, null, null, null, null), page);

        assertTrue(resultat.isEmpty());
        verify(bonCommandeMereRepo, never()).rechercher(any(), anyBoolean(), any(), any(), any(),
                any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }
}
