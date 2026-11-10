package afg.achat.afgApprovAchat.service.workflow;

import afg.achat.afgApprovAchat.email.EmailSenderService;
import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.ModeTraitement;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.demande.ValidationDemandeService;
import afg.achat.afgApprovAchat.service.util.CommentaireFinanceService;
import afg.achat.afgApprovAchat.service.util.ModeTraitementService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceTest {

    @Mock UtilisateurService utilisateurService;
    @Mock DemandeMereService demandeMereService;
    @Mock DemandeFilleService demandeFilleService;
    @Mock ValidationDemandeService validationDemandeService;
    @Mock PieceJointeWorkflowService pieceJointeService;
    @Mock CentreBudgetaireService centreBudgetaireService;
    @Mock ModeTraitementService modeTraitementService;
    @Mock CommentaireFinanceService commentaireFinanceService;
    @Mock EmailSenderService ess;

    @InjectMocks WorkflowService service;

    private Utilisateur demandeur;
    private Utilisateur current;
    private DemandeMere demande;

    @BeforeEach
    void setUp() {
        demandeur = utilisateur(10);
        current = utilisateur(20);
        demande = new DemandeMere();
        var idGenerator = mock(afg.achat.afgApprovAchat.service.util.IdGenerator.class);
        when(idGenerator.generateId(any(), any())).thenReturn("DM1");
        demande.setId(idGenerator);
        demande.setDemandeur(demandeur);
        demande.setStatut(StatutDemande.CREE);

        when(utilisateurService.getIdsUtilisateurVisible(anyInt())).thenReturn(List.of(20));
        when(utilisateurService.getIdsUtilisateursAValider(any())).thenReturn(List.of());
        when(demandeFilleService.getDemandeFilleByDemandeMere(any())).thenReturn(List.of());
        when(pieceJointeService.enregistrerPourDemande(any(), any(), any())).thenReturn(List.of());
        when(pieceJointeService.enregistrerPourCodep(any(), any(), any())).thenReturn(List.of());
    }

    // ── Droits ───────────────────────────────────────────────────────────────

    @Test
    void nPlus1PeutDeciderALaCreation() {
        when(utilisateurService.getIdsUtilisateurVisible(20)).thenReturn(List.of(20, 10));
        DroitsDecision d = service.calculerDroits(demande, current, auth());
        assertTrue(d.estNPlus1());
        assertTrue(d.n1());
    }

    @Test
    void validateurAssignePeutDeciderALaCreation() {
        when(utilisateurService.getIdsUtilisateursAValider(20)).thenReturn(List.of(10));
        assertTrue(service.calculerDroits(demande, current, auth()).n1());
    }

    @Test
    void mgNePeutPasDeciderHorsDeSonEtape() {
        demande.setStatut(StatutDemande.VALIDATION_N2);
        DroitsDecision d = service.calculerDroits(demande, current, auth("ROLE_MOYENS_GENERAUX"));
        assertFalse(d.auMoinsUn());
    }

    @Test
    void chaqueRoleDecideASonEtape() {
        demande.setStatut(StatutDemande.VALIDATION_N1);
        assertTrue(service.calculerDroits(demande, current, auth("ROLE_MOYENS_GENERAUX")).mg());
        demande.setStatut(StatutDemande.DECISION_CODEP);
        assertTrue(service.calculerDroits(demande, current, auth("ROLE_MOYENS_GENERAUX")).codep());
        demande.setStatut(StatutDemande.VALIDATION_N2);
        assertTrue(service.calculerDroits(demande, current, auth("ROLE_CONTROLEUR")).controleur());
        demande.setStatut(StatutDemande.VALIDATION_N3);
        assertTrue(service.calculerDroits(demande, current, auth("ROLE_DFC")).dfc());
        demande.setStatut(StatutDemande.VALIDATION_N4);
        assertTrue(service.calculerDroits(demande, current, auth("ROLE_SG")).sg());
    }

    @Test
    void financeAccedeAuxPaiementsDirectsTransmisSeulement() {
        Authentication finance = auth("ROLE_FINANCE_CONSULT");
        demande.setStatutTransmissionFinance(DemandeMere.StatutTransmissionFinance.A_TRANSMETTRE);
        assertFalse(service.aAccesDemande(demande, current, finance));
        demande.setStatutTransmissionFinance(DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE);
        assertTrue(service.aAccesDemande(demande, current, finance));
    }

    @Test
    void utilisateurHorsHierarchieSansAcces() {
        assertFalse(service.aAccesDemande(demande, current, auth()));
    }

    // ── Approbation ──────────────────────────────────────────────────────────

    @Test
    void sansDroitLaDecisionEstRefusee() {
        WorkflowException e = assertThrows(WorkflowException.class,
                () -> service.approuver(demande, current, auth(), cmd()));
        assertEquals("Cette demande ne peut pas être traitée par vous.", e.getMessage());
        verify(demandeMereService, never()).appliquerDecisionGlobale(any(), anyInt());
    }

    @Test
    void approbationN1PasseAuxMGEtLesNotifie() {
        when(utilisateurService.getIdsUtilisateurVisible(20)).thenReturn(List.of(20, 10));
        Utilisateur mg = utilisateur(30);
        when(utilisateurService.getUtilisateursByRole("MOYENS_GENERAUX")).thenReturn(List.of(mg));

        service.approuver(demande, current, auth(), cmd());

        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N1);
        verify(validationDemandeService).logValidation(demande, current, "ok", StatutDemande.CREE);
        verify(ess).envoyerMailValidateur(demande, current, StatutDemande.CREE, StatutDemande.VALIDATION_N1, mg);
    }

    @Test
    void mgSansModeTraitementNEcritRien() {
        demande.setStatut(StatutDemande.VALIDATION_N1);
        DecisionDemande cmd = new DecisionDemande("ok", fichiers(), "OPEX", null, null, null, null);

        WorkflowException e = assertThrows(WorkflowException.class,
                () -> service.approuver(demande, current, auth("ROLE_MOYENS_GENERAUX"), cmd));

        assertEquals("Le mode de traitement est obligatoire.", e.getMessage());
        verify(pieceJointeService, never()).enregistrerPourDemande(any(), any(), any());
        verify(validationDemandeService, never()).journaliser(any(), any(), anyInt(), any());
        verify(demandeMereService, never()).appliquerDecisionGlobale(any(), anyInt());
    }

    @Test
    void mgAvecLigneSansPrixNEcritRien() {
        demande.setStatut(StatutDemande.VALIDATION_N1);
        DemandeFille sansPrix = new DemandeFille();
        sansPrix.setStatut(StatutDemande.VALIDATION_N1);
        when(demandeFilleService.getDemandeFilleByDemandeMere(demande)).thenReturn(List.of(sansPrix));

        DecisionDemande cmd = new DecisionDemande("ok", null, "OPEX", null, "1", null, null);
        WorkflowException e = assertThrows(WorkflowException.class,
                () -> service.approuver(demande, current, auth("ROLE_MOYENS_GENERAUX"), cmd));

        assertTrue(e.getMessage().startsWith("Impossible de valider : les articles suivants"));
        verify(demandeMereService, never()).appliquerDecisionGlobale(any(), anyInt());
    }

    @Test
    void mgValideApplique_typePrioriteEtMode() {
        demande.setStatut(StatutDemande.VALIDATION_N1);
        demande.setPriorite(DemandeMere.PrioriteDemande.P2);
        ModeTraitement mode = new ModeTraitement();
        mode.setLibelle("Achat");
        when(modeTraitementService.getModeTraitementById(1)).thenReturn(mode);

        DecisionDemande cmd = new DecisionDemande("ok", null, "capex", "P0", "1", null, null);
        String message = service.approuver(demande, current, auth("ROLE_MOYENS_GENERAUX"), cmd);

        assertEquals("Demande validée par les Moyens Généraux (N2).", message);
        assertEquals(DemandeMere.NatureDemande.CAPEX, demande.getNatureDemande());
        assertEquals(DemandeMere.PrioriteDemande.P0, demande.getPriorite());
        assertSame(mode, demande.getModeTraitement());
        verify(demandeMereService).recalculerTotal(demande);
        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
    }

    @Test
    void controleurSansCommentaireNEcritRien() {
        demande.setStatut(StatutDemande.VALIDATION_N2);
        DecisionDemande cmd = new DecisionDemande("ok", null, null, null, null, "5", "  ");

        WorkflowException e = assertThrows(WorkflowException.class,
                () -> service.approuver(demande, current, auth("ROLE_CONTROLEUR"), cmd));

        assertEquals("Le commentaire du contrôleur de gestion est obligatoire.", e.getMessage());
        verify(commentaireFinanceService, never()).insertCommentaireFinance(any());
        verify(demandeMereService, never()).appliquerDecisionGlobale(any(), anyInt());
    }

    @Test
    void controleurSansLigneBudgetaireMessageClair() {
        demande.setStatut(StatutDemande.VALIDATION_N2);
        DecisionDemande cmd = new DecisionDemande("ok", null, null, null, null, null, "avis");
        WorkflowException e = assertThrows(WorkflowException.class,
                () -> service.approuver(demande, current, auth("ROLE_CONTROLEUR"), cmd));
        assertEquals("Ligne budgétaire obligatoire pour le contrôleur de gestion.", e.getMessage());
    }

    @Test
    void controleurValideRattacheLaLigneBudgetaire() {
        demande.setStatut(StatutDemande.VALIDATION_N2);
        CentreBudgetaire cb = new CentreBudgetaire();
        when(centreBudgetaireService.getCentreBudgetaireById(5)).thenReturn(cb);

        service.approuver(demande, current, auth("ROLE_CONTROLEUR"),
                new DecisionDemande("ok", null, null, null, null, "5", "avis"));

        assertSame(cb, demande.getCentreBudgetaire());
        verify(commentaireFinanceService).insertCommentaireFinance(any());
        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N3);
    }

    @Test
    void codepNotifieLesControleurs() {
        demande.setStatut(StatutDemande.DECISION_CODEP);
        Utilisateur controleur = utilisateur(40);
        when(utilisateurService.getUtilisateursByRole("CONTROLEUR")).thenReturn(List.of(controleur));

        service.approuver(demande, current, auth("ROLE_MOYENS_GENERAUX"), cmd());

        verify(pieceJointeService).enregistrerPourCodep(any(), eq(demande), eq("PV_CODEP"));
        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
        verify(ess).envoyerMailValidateur(demande, current, StatutDemande.DECISION_CODEP,
                StatutDemande.VALIDATION_N2, controleur);
    }

    @Test
    void validationSGPrepareLaTransmissionFinance() {
        demande.setStatut(StatutDemande.VALIDATION_N4);
        service.approuver(demande, current, auth("ROLE_SG"), cmd());

        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.VALIDE);
        verify(demandeMereService).preparerTransmissionFinance(demande);
        verify(ess).envoyerMailValidateur(demande, current, StatutDemande.VALIDATION_N4, StatutDemande.VALIDE, null);
    }

    @Test
    void adminForceLeCodepVersLeControleur() {
        demande.setStatut(StatutDemande.DECISION_CODEP);
        service.approuver(demande, current, auth("ROLE_ADMIN"), cmd());
        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N2);
    }

    @Test
    void adminForceVersValidePrepareLaTransmissionFinance() {
        demande.setStatut(StatutDemande.VALIDATION_N4);
        service.approuver(demande, current, auth("ROLE_ADMIN"), cmd());
        verify(demandeMereService).preparerTransmissionFinance(demande);
    }

    @Test
    void adminNeFaitPasAvancerUneDemandeClose() {
        demande.setStatut(StatutDemande.VALIDE);
        assertThrows(WorkflowException.class,
                () -> service.approuver(demande, current, auth("ROLE_ADMIN"), cmd()));
        verify(demandeMereService, never()).appliquerDecisionGlobale(any(), anyInt());
    }

    @Test
    void pieceJointeRefuseeBloqueLaValidation() {
        demande.setStatut(StatutDemande.VALIDATION_N3);
        doThrow(new WorkflowException("Fichier refusé")).when(pieceJointeService).verifierFormats(any());

        assertThrows(WorkflowException.class,
                () -> service.approuver(demande, current, auth("ROLE_DFC"), cmd()));
        verify(demandeMereService, never()).appliquerDecisionGlobale(any(), anyInt());
    }

    // ── Rejet ────────────────────────────────────────────────────────────────

    @Test
    void rejetSansCommentaireRefuse() {
        demande.setStatut(StatutDemande.VALIDATION_N3);
        DecisionDemande cmd = new DecisionDemande("  ", null, null, null, null, null, null);
        assertThrows(WorkflowException.class,
                () -> service.rejeter(demande, current, auth("ROLE_DFC"), cmd));
        verify(demandeMereService, never()).appliquerDecisionGlobale(any(), anyInt());
    }

    @Test
    void rejetJournaliseEtRefuse() {
        demande.setStatut(StatutDemande.VALIDATION_N3);
        service.rejeter(demande, current, auth("ROLE_DFC"), cmd());

        verify(validationDemandeService).logRefus(demande, current, "ok", StatutDemande.VALIDATION_N3);
        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.REFUSE);
        verify(ess).sendEmail(any());
    }

    // ── Envoi au CODEP ───────────────────────────────────────────────────────

    @Test
    void envoiCodepReserveAuxMGALEtapeMG() {
        demande.setStatut(StatutDemande.VALIDATION_N2);
        assertThrows(WorkflowException.class, () -> service.envoyerAuCodep(
                demande, current, auth("ROLE_MOYENS_GENERAUX"), "OPEX", null, null));
    }

    @Test
    void envoiCodepMarqueLaDemande() {
        demande.setStatut(StatutDemande.VALIDATION_N1);
        service.envoyerAuCodep(demande, current, auth("ROLE_MOYENS_GENERAUX"), "OPEX", "P1", null);

        assertTrue(demande.getViaCodep());
        assertEquals(DemandeMere.NatureDemande.OPEX, demande.getNatureDemande());
        verify(demandeMereService).appliquerDecisionGlobale(demande, StatutDemande.DECISION_CODEP);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Utilisateur utilisateur(int id) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setMail("u" + id + "@test.local");
        return u;
    }

    private static Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("u", null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private static DecisionDemande cmd() {
        return new DecisionDemande("ok", null, null, null, null, null, null);
    }

    private static MultipartFile[] fichiers() {
        return new MultipartFile[]{
                new MockMultipartFile("piecesJointes", "devis.pdf", "application/pdf", new byte[]{1})
        };
    }
}
