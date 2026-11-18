package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseService;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonCommandeDemandeServiceTest {

    @Mock BonCommandeMereRepo bonCommandeMereRepo;
    @Mock DemandeMereService demandeMereService;
    @Mock DemandeFilleService demandeFilleService;
    @Mock FournisseurService fournisseurService;
    @Mock ResponsableFournisseurService responsableFournisseurService;
    @Mock ResponsableService responsableService;
    @Mock AdresseFournisseurService adresseFournisseurService;
    @Mock AdresseService adresseService;
    @Mock IdGenerator idGenerator;

    @InjectMocks BonCommandeDemandeService service;

    private final Authentication mg = auth("ROLE_MOYENS_GENERAUX");
    private final Utilisateur current = new Utilisateur();
    private DemandeMere demande;

    @BeforeEach
    void setUp() {
        when(idGenerator.generateId(eq("DM"), any())).thenReturn("DM1");
        when(idGenerator.generateId(eq("BC"), any())).thenReturn("BC1");
        when(idGenerator.generateNumeroBC()).thenReturn("18112026-1");
        demande = new DemandeMere();
        demande.setId(idGenerator);
        demande.setStatut(StatutDemande.VALIDE);

        when(demandeMereService.getDemandeById("DM1")).thenReturn(demande);
        when(demandeMereService.peutCreerBonCommande(demande, true)).thenReturn(true);
        when(bonCommandeMereRepo.findByDemandeMereOrderByDateCreationDesc(demande)).thenReturn(List.of());
        when(bonCommandeMereRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── Création ─────────────────────────────────────────────────────────────

    @Test
    void creationAvecSeulementLesLignesACommander() {
        DemandeFille achat = ligne(1, 2, 1000.0, DemandeFille.TypeApprovisionnement.ACHAT, StatutDemande.VALIDE);
        DemandeFille stock = ligne(2, 3, 500.0, DemandeFille.TypeApprovisionnement.STOCK, StatutDemande.VALIDE);
        DemandeFille refusee = ligne(3, 1, 800.0, DemandeFille.TypeApprovisionnement.ACHAT, StatutDemande.REFUSE);
        when(demandeFilleService.getDemandeFilleByDemandeMere(demande)).thenReturn(List.of(achat, stock, refusee));

        BonCommandeMere bc = service.creer("DM1", current, mg);

        assertEquals(StatutBonCommande.BROUILLON, bc.getStatut());
        assertEquals(1, bc.getLignes().size());
        assertSame(achat, bc.getLignes().get(0).getDemandeFille());
        assertEquals(2000.0, bc.getMontantHT(), 0.001);
        assertEquals(2000.0, bc.getMontantTTC(), 0.001);
        assertSame(current, bc.getCreateur());
    }

    @Test
    void unBonActifExistantEstRenvoyeSansEnCreerUnAutre() {
        BonCommandeMere annule = new BonCommandeMere();
        annule.setStatut(StatutBonCommande.ANNULE);
        BonCommandeMere actif = new BonCommandeMere();
        actif.setStatut(StatutBonCommande.EN_ATTENTE_VALIDATION);
        when(bonCommandeMereRepo.findByDemandeMereOrderByDateCreationDesc(demande)).thenReturn(List.of(annule, actif));

        assertSame(actif, service.creer("DM1", current, mg));
        verify(bonCommandeMereRepo, never()).save(any());
    }

    @Test
    void unBonAnnuleNEmpechePasDEnRecreerUn() {
        BonCommandeMere annule = new BonCommandeMere();
        annule.setStatut(StatutBonCommande.ANNULE);
        when(bonCommandeMereRepo.findByDemandeMereOrderByDateCreationDesc(demande)).thenReturn(List.of(annule));
        when(demandeFilleService.getDemandeFilleByDemandeMere(demande)).thenReturn(
                List.of(ligne(1, 1, 10.0, DemandeFille.TypeApprovisionnement.ACHAT, StatutDemande.VALIDE)));

        assertNotSame(annule, service.creer("DM1", current, mg));
        verify(bonCommandeMereRepo).save(any());
    }

    @Test
    void creationRefuseeSiLaDemandeNeLePermetPas() {
        when(demandeMereService.peutCreerBonCommande(demande, true)).thenReturn(false);
        assertThrows(WorkflowException.class, () -> service.creer("DM1", current, mg));
        assertThrows(WorkflowException.class, () -> service.creer("INCONNUE", current, mg));
        assertThrows(WorkflowException.class, () -> service.creer("DM1", current, auth("ROLE_MG_VALIDATOR")));
        verify(bonCommandeMereRepo, never()).save(any());
    }

    @Test
    void creationRefuseeSansLigneACommander() {
        when(demandeFilleService.getDemandeFilleByDemandeMere(demande)).thenReturn(
                List.of(ligne(1, 1, 10.0, DemandeFille.TypeApprovisionnement.STOCK, StatutDemande.VALIDE)));
        assertThrows(WorkflowException.class, () -> service.creer("DM1", current, mg));
    }

    // ── Saisie ───────────────────────────────────────────────────────────────

    @Test
    void enregistrementRecalculeLesMontantsDepuisLaDemande() {
        BonCommandeMere bc = brouillon();
        DemandeFille df = ligne(1, 4, 250.0, DemandeFille.TypeApprovisionnement.ACHAT, StatutDemande.VALIDE);
        BonCommandeFille ligne = new BonCommandeFille();
        ligne.setId(11);
        ligne.setDemandeFille(df);
        ligne.setPrixUnitaireHT(1);       // valeur obsolète : remplacée par le prix de la demande
        ligne.setMontantHT(999_999);
        bc.getLignes().add(ligne);

        Fournisseur fournisseur = new Fournisseur();
        fournisseur.setId(7);
        when(fournisseurService.getFournisseurById(7)).thenReturn(Optional.of(fournisseur));

        service.enregistrer(saisie("7", 10.0, 20.0, Map.of(11, " Ecran 24 pouces ")), mg);

        assertSame(fournisseur, bc.getFournisseur());
        assertEquals("Ecran 24 pouces", ligne.getDesignationFournisseur());
        assertEquals(1000.0, bc.getMontantHT(), 0.001);
        assertEquals(10.0, bc.getRemise());
        // 1000 − 10 % = 900 ; + 20 % de TVA = 1080
        assertEquals(1080.0, bc.getMontantTTC(), 0.001);
    }

    @Test
    void unBonSoumisNEstPlusModifiable() {
        BonCommandeMere bc = brouillon();
        bc.setStatut(StatutBonCommande.VALIDE);
        assertThrows(WorkflowException.class, () -> service.enregistrer(saisie(null, null, null, Map.of()), mg));
        verify(bonCommandeMereRepo, never()).save(any());
    }

    @Test
    void valeursInvalidesRefusees() {
        brouillon();
        assertThrows(WorkflowException.class, () -> service.enregistrer(saisie(null, 120.0, null, Map.of()), mg));
        assertThrows(WorkflowException.class, () -> service.enregistrer(saisie(null, null, -5.0, Map.of()), mg));
        assertThrows(WorkflowException.class, () -> service.enregistrer(saisie("abc", null, null, Map.of()), mg));
        verify(bonCommandeMereRepo, never()).save(any());
    }

    @Test
    void numeroDejaUtiliseRefuse() {
        brouillon();
        when(bonCommandeMereRepo.existsByNumero("AUTRE-1")).thenReturn(true);
        SaisieBonCommandeDemande saisie = new SaisieBonCommandeDemande("BC9", "AUTRE-1", null, null, null, null,
                null, null, null, null, Map.of());
        assertThrows(WorkflowException.class, () -> service.enregistrer(saisie, mg));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BonCommandeMere brouillon() {
        BonCommandeMere bc = new BonCommandeMere();
        bc.setNumero("18112026-1");
        bc.setStatut(StatutBonCommande.BROUILLON);
        bc.setDemandeMere(demande);
        when(bonCommandeMereRepo.findById("BC9")).thenReturn(Optional.of(bc));
        return bc;
    }

    private static SaisieBonCommandeDemande saisie(String fournisseurId, Double remise, Double tva,
                                                  Map<Integer, String> designations) {
        return new SaisieBonCommandeDemande("BC9", "", "2026-12-01", fournisseurId, "DEVIS-1", "Siège",
                remise, tva, null, null, designations);
    }

    private static DemandeFille ligne(int id, double quantite, double prix,
                                      DemandeFille.TypeApprovisionnement type, int statut) {
        DemandeFille df = new DemandeFille();
        df.setId(id);
        df.setArticle(new Article());
        df.setQuantite(String.valueOf(quantite));
        df.setPrixUnitaire(prix);
        df.setTypeApprovisionnement(type);
        df.setStatut(statut);
        return df;
    }

    private static Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("u", null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
}
