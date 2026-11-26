package afg.achat.afgApprovAchat.service.perdiem;

import afg.achat.afgApprovAchat.model.perdiem.BaremePerDiem;
import afg.achat.afgApprovAchat.model.perdiem.BaremePerDiem.Statut;
import afg.achat.afgApprovAchat.model.perdiem.CategoriePersonnel;
import afg.achat.afgApprovAchat.model.perdiem.TypeIndemnite;
import afg.achat.afgApprovAchat.model.perdiem.ZoneMission;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.perdiem.BaremePerDiemRepo;
import afg.achat.afgApprovAchat.repository.util.DeviseRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaremePerDiemServiceTest {

    @Mock BaremePerDiemRepo baremeRepo;
    @Mock ReferentielMissionService referentielService;
    @Mock DeviseRepo deviseRepo;

    @InjectMocks BaremePerDiemService service;

    private final Authentication maker = auth("ROLE_RH", "ROLE_MAKER");
    private final Authentication drh = auth("ROLE_RH", "ROLE_CHECKER");
    private final Authentication rhSeul = auth("ROLE_RH");
    private final Authentication admin = auth("ROLE_ADMIN");

    private Utilisateur gestionnaire;
    private Utilisateur directeurRh;
    private CategoriePersonnel cadre;
    private ZoneMission province;
    private Devise mga;

    @BeforeEach
    void setUp() {
        gestionnaire = utilisateur(1);
        directeurRh = utilisateur(2);
        cadre = new CategoriePersonnel(10, "CADRE", "Cadre", 1, true);
        province = new ZoneMission(20, "PROVINCE", "Chef-lieu de province", false, 1, true);
        mga = new Devise();
        mga.setId(1);
        mga.setAcronyme("MGA");

        when(referentielService.categorie(10)).thenReturn(cadre);
        when(referentielService.zone(20)).thenReturn(province);
        when(deviseRepo.findByAcronymeIgnoreCase("MGA")).thenReturn(Optional.of(mga));
        when(baremeRepo.findLigne(anyInt(), anyInt(), any(), any())).thenReturn(List.of());
        when(baremeRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(baremeRepo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── Proposition ──────────────────────────────────────────────────────────

    @Test
    void leMakerProposeUnTauxEnAttenteEnAriaryParDefaut() {
        BaremePerDiem b = service.proposer(maker, gestionnaire, 10, 20, "REPAS",
                "45 000", null, LocalDate.of(2027, 1, 1), " Note DRH ");

        assertEquals(Statut.PROPOSE, b.getStatut());
        assertEquals(new BigDecimal("45000"), b.getMontant());
        assertEquals(mga, b.getDevise());
        assertEquals(TypeIndemnite.REPAS, b.getTypeIndemnite());
        assertEquals(gestionnaire, b.getProposePar());
        assertEquals("Note DRH", b.getMotif());
        assertNull(b.getDateFin());
    }

    @Test
    void unRhSansMakerNePropose() {
        assertThrows(AccessDeniedException.class, () -> service.proposer(rhSeul, gestionnaire, 10, 20,
                "REPAS", "45000", null, LocalDate.of(2027, 1, 1), null));
        verify(baremeRepo, never()).save(any());
    }

    @Test
    void uneSeulePropositionEnAttenteParCase() {
        when(baremeRepo.findLigne(10, 20, TypeIndemnite.REPAS, Statut.PROPOSE))
                .thenReturn(List.of(taux(5, Statut.PROPOSE, LocalDate.of(2027, 1, 1), null)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.proposer(
                maker, gestionnaire, 10, 20, "REPAS", "50000", null, LocalDate.of(2027, 2, 1), null));
        assertTrue(e.getMessage().contains("déjà en attente"));
    }

    @Test
    void laDateEffetSuitLeDernierTauxValide() {
        when(baremeRepo.findLigne(10, 20, TypeIndemnite.REPAS, Statut.VALIDE))
                .thenReturn(List.of(taux(5, Statut.VALIDE, LocalDate.of(2027, 1, 1), null)));

        assertThrows(IllegalArgumentException.class, () -> service.proposer(maker, gestionnaire, 10, 20,
                "REPAS", "50000", null, LocalDate.of(2027, 1, 1), null));
        assertDoesNotThrow(() -> service.proposer(maker, gestionnaire, 10, 20,
                "REPAS", "50000", null, LocalDate.of(2027, 1, 2), null));
    }

    @Test
    void uneCategorieDesactiveeNEstPlusProposee() {
        cadre.setActif(false);
        assertThrows(IllegalArgumentException.class, () -> service.proposer(maker, gestionnaire, 10, 20,
                "REPAS", "45000", null, LocalDate.of(2027, 1, 1), null));
    }

    @Test
    void lectureDuMontant() {
        assertEquals(new BigDecimal("45000.50"), BaremePerDiemService.lireMontant("45 000,50"));
        assertEquals(new BigDecimal("120"), BaremePerDiemService.lireMontant("120"));
        assertThrows(IllegalArgumentException.class, () -> BaremePerDiemService.lireMontant("0"));
        assertThrows(IllegalArgumentException.class, () -> BaremePerDiemService.lireMontant("-5"));
        assertThrows(IllegalArgumentException.class, () -> BaremePerDiemService.lireMontant("12,345"));
        assertThrows(IllegalArgumentException.class, () -> BaremePerDiemService.lireMontant("abc"));
        assertThrows(IllegalArgumentException.class, () -> BaremePerDiemService.lireMontant(" "));
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    void leDrhValideEtClotLeTauxPrecedent() {
        BaremePerDiem ancien = taux(5, Statut.VALIDE, LocalDate.of(2026, 1, 1), null);
        BaremePerDiem propose = taux(6, Statut.PROPOSE, LocalDate.of(2027, 1, 1), gestionnaire);
        when(baremeRepo.findById(6)).thenReturn(Optional.of(propose));
        when(baremeRepo.findLigne(10, 20, TypeIndemnite.REPAS, Statut.VALIDE)).thenReturn(List.of(ancien));

        BaremePerDiem b = service.valider(drh, directeurRh, 6, null);

        assertEquals(Statut.VALIDE, b.getStatut());
        assertEquals(directeurRh, b.getTraitePar());
        assertNotNull(b.getDateTraitement());
        assertEquals(LocalDate.of(2027, 1, 1), ancien.getDateFin());
        assertTrue(ancien.estApplicableLe(LocalDate.of(2026, 12, 31)));
        assertFalse(ancien.estApplicableLe(LocalDate.of(2027, 1, 1)));
        assertTrue(b.estApplicableLe(LocalDate.of(2027, 1, 1)));
        verify(baremeRepo).saveAndFlush(ancien);
    }

    @Test
    void personneNeValideSaPropreProposition() {
        BaremePerDiem propose = taux(6, Statut.PROPOSE, LocalDate.of(2027, 1, 1), directeurRh);
        when(baremeRepo.findById(6)).thenReturn(Optional.of(propose));

        assertThrows(AccessDeniedException.class, () -> service.valider(drh, directeurRh, 6, null));
        assertEquals(Statut.PROPOSE, propose.getStatut());
    }

    @Test
    void leMakerNeValidePas() {
        BaremePerDiem propose = taux(6, Statut.PROPOSE, LocalDate.of(2027, 1, 1), directeurRh);
        when(baremeRepo.findById(6)).thenReturn(Optional.of(propose));

        assertThrows(AccessDeniedException.class, () -> service.valider(maker, gestionnaire, 6, null));
    }

    @Test
    void lAdministrateurValideMemeSaProposition() {
        BaremePerDiem propose = taux(6, Statut.PROPOSE, LocalDate.of(2027, 1, 1), directeurRh);
        when(baremeRepo.findById(6)).thenReturn(Optional.of(propose));

        assertEquals(Statut.VALIDE, service.valider(admin, directeurRh, 6, null).getStatut());
    }

    @Test
    void unePropositionDejaTraiteeNeSeValidePlus() {
        when(baremeRepo.findById(6)).thenReturn(Optional.of(taux(6, Statut.REJETE, LocalDate.of(2027, 1, 1), gestionnaire)));

        assertThrows(IllegalArgumentException.class, () -> service.valider(drh, directeurRh, 6, null));
    }

    // ── Rejet et annulation ──────────────────────────────────────────────────

    @Test
    void leRejetExigeUnMotif() {
        BaremePerDiem propose = taux(6, Statut.PROPOSE, LocalDate.of(2027, 1, 1), gestionnaire);
        when(baremeRepo.findById(6)).thenReturn(Optional.of(propose));

        assertThrows(IllegalArgumentException.class, () -> service.rejeter(drh, directeurRh, 6, "  "));
        BaremePerDiem b = service.rejeter(drh, directeurRh, 6, "Montant hors note");
        assertEquals(Statut.REJETE, b.getStatut());
        assertEquals("Montant hors note", b.getCommentaireTraitement());
    }

    @Test
    void seulLAuteurAnnuleSaProposition() {
        BaremePerDiem propose = taux(6, Statut.PROPOSE, LocalDate.of(2027, 1, 1), gestionnaire);
        when(baremeRepo.findById(6)).thenReturn(Optional.of(propose));

        assertThrows(AccessDeniedException.class, () -> service.annuler(drh, directeurRh, 6));
        assertEquals(Statut.ANNULE, service.annuler(maker, gestionnaire, 6).getStatut());
    }

    // ── Grille ───────────────────────────────────────────────────────────────

    @Test
    void laGrilleIndexeParTypeCategorieZone() {
        BaremePerDiem repas = taux(5, Statut.VALIDE, LocalDate.of(2026, 1, 1), null);
        when(baremeRepo.findApplicablesLe(any())).thenReturn(List.of(repas));

        var grille = service.grille(LocalDate.of(2026, 6, 1));

        assertSame(repas, grille.get("REPAS").get(10).get(20));
        assertTrue(grille.get("HEBERGEMENT").isEmpty());
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private BaremePerDiem taux(int id, Statut statut, LocalDate dateEffet, Utilisateur auteur) {
        BaremePerDiem b = new BaremePerDiem();
        b.setId(id);
        b.setCategorie(cadre);
        b.setZone(province);
        b.setTypeIndemnite(TypeIndemnite.REPAS);
        b.setMontant(new BigDecimal("40000"));
        b.setDevise(mga);
        b.setDateEffet(dateEffet);
        b.setStatut(statut);
        b.setProposePar(auteur);
        return b;
    }

    private static Utilisateur utilisateur(int id) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        return u;
    }

    private static Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("u", "p",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
}
