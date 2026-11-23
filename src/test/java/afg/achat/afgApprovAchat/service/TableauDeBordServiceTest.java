package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.repository.demande.DemandeMereRepo;
import afg.achat.afgApprovAchat.repository.demande.ValidationDemandeRepo;
import afg.achat.afgApprovAchat.service.bonCommande.BesoinAchatService;
import afg.achat.afgApprovAchat.service.stock.StockAlerteService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableauDeBordServiceTest {

    @Mock DemandeMereRepo demandeMereRepo;
    @Mock ValidationDemandeRepo validationDemandeRepo;
    @Mock BonCommandeMereRepo bonCommandeMereRepo;
    @Mock BesoinAchatService besoinAchatService;
    @Mock StockAlerteService stockAlerteService;
    @Mock UtilisateurService utilisateurService;
    @Mock SortieAFaireService sortieAFaireService;
    @InjectMocks TableauDeBordService service;

    private static final LocalDateTime D0 = LocalDateTime.of(2026, 10, 1, 8, 0);

    private static Object[] action(String id, int statutActuel, int etape, double joursApres) {
        return new Object[]{id, D0, statutActuel, etape, D0.plusMinutes((long) (joursApres * 24 * 60))};
    }

    @Test
    void delaiParEtapeMesureEntreeEtSortieEtIgnoreLEtapeEnCours() {
        when(validationDemandeRepo.dernieresActionsParEtape(any(), any())).thenReturn(List.of(
                // Demande A, validée : N+1 1 j, M.G. 2 j, Contrôle 1 j, D.F.C. 0,5 j, S.G. 1,5 j
                action("A", 15, 1, 1), action("A", 15, 11, 3), action("A", 15, 12, 4),
                action("A", 15, 13, 4.5), action("A", 15, 14, 6),
                // Demande B, encore chez M.G. : N+1 2 j ; l'étape M.G. en cours est ignorée
                action("B", 11, 1, 2), action("B", 11, 11, 2.5),
                // Action après validation (étape « validée ») : hors circuit, ignorée
                action("A", 15, 15, 9)));

        List<TableauDeBordService.DelaiEtape> delais = service.delaisParEtape(D0, D0.plusDays(30));

        assertEquals(List.of("N+1", "Moyens Généraux", "Contrôleur de gestion", "D.F.C.", "S.G."),
                delais.stream().map(TableauDeBordService.DelaiEtape::etape).toList());
        TableauDeBordService.DelaiEtape n1 = delais.get(0);
        assertEquals(1.5, n1.joursMoyens());
        assertEquals(2, n1.nombre());
        TableauDeBordService.DelaiEtape mg = delais.get(1);
        assertEquals(2.0, mg.joursMoyens());
        assertEquals(1, mg.nombre());
        assertTrue(mg.plusLente());
        assertEquals(25, delais.get(3).largeur()); // 0,5 j sur 2 j
    }

    @Test
    void bonsDeCommandeAValiderSeulementPourLEtapeDuRole() {
        Utilisateur sg = new Utilisateur();
        sg.setId(7);
        when(utilisateurService.getIdsUtilisateurVisible(7)).thenReturn(List.of(7));
        when(utilisateurService.getIdsUtilisateursAValider(7)).thenReturn(List.of());
        when(demandeMereRepo.compterParStatut(any())).thenReturn(List.of());
        when(bonCommandeMereRepo.compterEnAttenteParEtape(BonCommandeMere.StatutBonCommande.EN_ATTENTE_VALIDATION))
                .thenReturn(List.of(
                        new Object[]{BonCommandeMere.EtapeValidation.MG, 4L, D0},
                        new Object[]{BonCommandeMere.EtapeValidation.SG, 2L, LocalDateTime.now().minusDays(1)}));
        Authentication auth = new UsernamePasswordAuthenticationToken(sg, null,
                List.of(new SimpleGrantedAuthority("ROLE_SG")));

        List<TableauDeBordService.Action> actions = service.actions(sg, auth);

        TableauDeBordService.Action bc = actions.stream()
                .filter(a -> a.titre().equals("Bons de commande à valider")).findFirst().orElseThrow();
        assertEquals(2, bc.nombre());              // les 4 bons à l'étape M.G. ne le concernent pas
        assertEquals(1L, bc.joursAttente());
        assertFalse(bc.urgent());
        assertEquals("/bon-commande/list?aValider=true", bc.url());
        // Aucun accès achats / réception / stock pour le seul rôle SG
        assertTrue(actions.stream().noneMatch(a -> a.url().startsWith("/stock")
                || a.url().contains("aRecevoir") || a.url().contains("lignes-a-acheter")));
        verifyNoInteractions(besoinAchatService, stockAlerteService, sortieAFaireService);
    }

    @Test
    void rienATraiterQuandToutEstVide() {
        Utilisateur u = new Utilisateur();
        u.setId(3);
        when(utilisateurService.getIdsUtilisateurVisible(3)).thenReturn(List.of(3, 4));
        when(utilisateurService.getIdsUtilisateursAValider(3)).thenReturn(List.of());
        when(demandeMereRepo.compterStatutPourDemandeurs(anyInt(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{0L, null}));
        Authentication auth = new UsernamePasswordAuthenticationToken(u, null, List.of());

        assertTrue(service.actions(u, auth).isEmpty());
    }
}
