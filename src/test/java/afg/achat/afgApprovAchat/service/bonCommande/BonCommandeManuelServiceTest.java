package afg.achat.afgApprovAchat.service.bonCommande;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.EtapeValidation;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableFournisseurService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonCommandeManuelServiceTest {

    @Mock BonCommandeMereRepo bonCommandeMereRepo;
    @Mock FournisseurService fournisseurService;
    @Mock AdresseFournisseurService adresseFournisseurService;
    @Mock ResponsableFournisseurService responsableFournisseurService;
    @Mock ArticleService articleService;
    @Mock IdGenerator idGenerator;

    @InjectMocks BonCommandeManuelService service;

    private final Authentication createur = auth("ROLE_MG_CREATOR");
    private final Authentication validateurMg = auth("ROLE_MG_VALIDATOR");
    private final Authentication sg = auth("ROLE_SG");
    private Utilisateur current;
    private BonCommandeMere bc;

    @BeforeEach
    void setUp() {
        current = new Utilisateur();
        current.setId(1);

        Fournisseur fournisseur = new Fournisseur();
        fournisseur.setId(7);
        when(fournisseurService.getFournisseurById(7)).thenReturn(Optional.of(fournisseur));
        when(adresseFournisseurService.getAdresseActuel(anyInt())).thenReturn(Optional.empty());
        when(responsableFournisseurService.getResponsableActuel(anyInt())).thenReturn(Optional.empty());
        when(idGenerator.generateNumeroBC()).thenReturn("13112026-1");
        when(bonCommandeMereRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Bon manuel en brouillon, avec une ligne (l'identifiant vient du générateur)
        bc = new BonCommandeMere();
        when(idGenerator.generateId(any(), any())).thenReturn("BC9");
        bc.setId(idGenerator);
        when(idGenerator.generateId(any(), any())).thenReturn("BC1");
        bc.setStatut(StatutBonCommande.BROUILLON);
        bc.getLignes().add(new BonCommandeFille());
        when(bonCommandeMereRepo.findById("BC9")).thenReturn(Optional.of(bc));
    }

    // ── Saisie ───────────────────────────────────────────────────────────────

    @Test
    void creationEnBrouillonAvecCalculDesMontants() {
        BonCommandeMere cree = service.enregistrer(saisie(null, 15.0, 20.0,
                ligne("Caisson lumineux", "2", "1 500,50"), ligne("Habillage mur", "1", "1000")), current, createur);

        assertEquals("BC1", cree.getId());
        assertEquals("13112026-1", cree.getNumero());
        assertEquals(StatutBonCommande.BROUILLON, cree.getStatut());
        assertSame(current, cree.getCreateur());
        assertTrue(cree.estManuel());
        assertEquals(2, cree.getLignes().size());
        assertEquals(3001.0, cree.getLignes().get(0).getMontantHT(), 0.001);

        assertEquals(4001.0, cree.getMontantHT(), 0.001);
        // 4001 − 15 % = 3400,85 ; + 20 % de TVA = 4081,02
        assertEquals(4081.02, cree.getMontantTTC(), 0.001);
        assertEquals(15.0, cree.getRemise());
    }

    @Test
    void lesLignesVidesSontIgnorees() {
        BonCommandeMere cree = service.enregistrer(saisie(null, null, null,
                ligne("Prestation", "1", "500"), new SaisieBonCommande.Ligne("", "", "", "Unité", "")),
                current, createur);
        assertEquals(1, cree.getLignes().size());
    }

    @Test
    void articleConcerneFacultatifMaisDoitExister() {
        Article article = new Article();
        when(articleService.getArticleByCodeArticle("ART1")).thenReturn(Optional.of(article));
        when(articleService.getArticleByCodeArticle("INCONNU")).thenReturn(Optional.empty());

        BonCommandeMere cree = service.enregistrer(saisie(null, null, null,
                new SaisieBonCommande.Ligne("Ecran", "ART1", "1", "Unité", "100")), current, createur);
        assertSame(article, cree.getLignes().get(0).getArticle());

        assertThrows(WorkflowException.class, () -> service.enregistrer(saisie(null, null, null,
                new SaisieBonCommande.Ligne("Ecran", "INCONNU", "1", "Unité", "100")), current, createur));
    }

    @Test
    void saisieIncompleteRefuseeSansRienEnregistrer() {
        assertThrows(WorkflowException.class,
                () -> service.enregistrer(saisie(null, null, null), current, createur));
        assertThrows(WorkflowException.class,
                () -> service.enregistrer(saisie(null, null, null, ligne("", "1", "10")), current, createur));
        assertThrows(WorkflowException.class,
                () -> service.enregistrer(saisie(null, null, null, ligne("Prestation", "0", "10")), current, createur));
        assertThrows(WorkflowException.class,
                () -> service.enregistrer(saisie(null, 150.0, null, ligne("Prestation", "1", "10")), current, createur));
        verify(bonCommandeMereRepo, never()).save(any());
    }

    @Test
    void seulUnCreateurPeutSaisir() {
        assertThrows(WorkflowException.class, () -> service.enregistrer(
                saisie(null, null, null, ligne("Prestation", "1", "10")), current, validateurMg));
        verify(bonCommandeMereRepo, never()).save(any());
    }

    @Test
    void modificationRemplaceLesLignesDuBrouillon() {
        BonCommandeMere modifie = service.enregistrer(saisie("BC9", null, null,
                ligne("Nouvelle ligne", "3", "10")), current, createur);

        assertSame(bc, modifie);
        assertEquals(1, modifie.getLignes().size());
        assertEquals("Nouvelle ligne", modifie.getLignes().get(0).getDesignation());
        assertEquals(30.0, modifie.getMontantTTC(), 0.001);
    }

    @Test
    void unBonSoumisNEstPlusModifiable() {
        bc.setStatut(StatutBonCommande.EN_ATTENTE_VALIDATION);
        bc.setEtapeValidation(EtapeValidation.MG);
        assertThrows(WorkflowException.class, () -> service.enregistrer(
                saisie("BC9", null, null, ligne("Prestation", "1", "10")), current, createur));
    }

    @Test
    void unBonIssuDUneDemandeNEstPasGereIci() {
        bc.setDemandeMere(new DemandeMere());
        assertFalse(BonCommandeManuelService.peutModifier(bc, createur));
        assertThrows(WorkflowException.class, () -> service.soumettre("BC9", current, createur));
    }

    // ── Circuit de validation ────────────────────────────────────────────────

    @Test
    void circuitCompletCreateurPuisMgPuisSg() {
        service.soumettre("BC9", current, createur);
        assertEquals(StatutBonCommande.EN_ATTENTE_VALIDATION, bc.getStatut());
        assertEquals(EtapeValidation.MG, bc.getEtapeValidation());
        assertSame(current, bc.getSoumisPar());

        service.valider("BC9", current, validateurMg);
        assertEquals(StatutBonCommande.EN_ATTENTE_VALIDATION, bc.getStatut());
        assertEquals(EtapeValidation.SG, bc.getEtapeValidation());
        assertSame(current, bc.getValideMgPar());
        assertNotNull(bc.getDateValidationMg());

        service.valider("BC9", current, sg);
        assertEquals(StatutBonCommande.VALIDE, bc.getStatut());
        assertNull(bc.getEtapeValidation());
        assertSame(current, bc.getValideSgPar());
        assertEquals("Validé", bc.getLibelleStatut());
    }

    @Test
    void chaqueEtapeEstReserveeASonRole() {
        service.soumettre("BC9", current, createur);

        // À l'étape MG : ni le S.G. ni le créateur ne valident
        assertThrows(WorkflowException.class, () -> service.valider("BC9", current, sg));
        assertThrows(WorkflowException.class, () -> service.valider("BC9", current, createur));

        service.valider("BC9", current, validateurMg);

        // À l'étape S.G. : le validateur MG ne valide plus
        assertThrows(WorkflowException.class, () -> service.valider("BC9", current, validateurMg));
        assertEquals(EtapeValidation.SG, bc.getEtapeValidation());
    }

    @Test
    void unBrouillonNeSeValidePas() {
        assertThrows(WorkflowException.class, () -> service.valider("BC9", current, validateurMg));
    }

    @Test
    void soumissionReserveeAuCreateurEtAuxBonsAvecLignes() {
        assertThrows(WorkflowException.class, () -> service.soumettre("BC9", current, validateurMg));

        bc.getLignes().clear();
        assertThrows(WorkflowException.class, () -> service.soumettre("BC9", current, createur));
        assertEquals(StatutBonCommande.BROUILLON, bc.getStatut());
    }

    @Test
    void rejetRenvoieEnBrouillonAvecLeMotif() {
        service.soumettre("BC9", current, createur);
        service.valider("BC9", current, validateurMg);

        assertThrows(WorkflowException.class, () -> service.rejeter("BC9", current, sg, "  "));
        service.rejeter("BC9", current, sg, " Montant à revoir ");

        assertEquals(StatutBonCommande.BROUILLON, bc.getStatut());
        assertNull(bc.getEtapeValidation());
        assertEquals("Montant à revoir", bc.getMotifRejet());
        assertSame(current, bc.getRejetePar());
    }

    @Test
    void nouvelleSoumissionEffaceLeRejetEtLesValidations() {
        service.soumettre("BC9", current, createur);
        service.valider("BC9", current, validateurMg);
        service.rejeter("BC9", current, sg, "Montant à revoir");

        service.soumettre("BC9", current, createur);

        assertEquals(EtapeValidation.MG, bc.getEtapeValidation());
        assertNull(bc.getMotifRejet());
        assertNull(bc.getValideMgPar());
    }

    @Test
    void adminAgitAChaqueEtape() {
        Authentication admin = auth("ROLE_ADMIN");
        service.soumettre("BC9", current, admin);
        service.valider("BC9", current, admin);
        service.valider("BC9", current, admin);
        assertEquals(StatutBonCommande.VALIDE, bc.getStatut());
    }

    @Test
    void annulationDUnBrouillonParLeCreateur() {
        assertThrows(WorkflowException.class, () -> service.annuler("BC9", sg));
        service.annuler("BC9", createur);
        assertEquals(StatutBonCommande.ANNULE, bc.getStatut());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static SaisieBonCommande saisie(String id, Double tauxRemise, Double tauxTVA,
                                            SaisieBonCommande.Ligne... lignes) {
        return new SaisieBonCommande(id, "7", "DEVIS-1", "2026-12-01", "IVATO",
                "70% à la commande", "CODEP 007/26", "BRANDING", tauxRemise, tauxTVA, List.of(lignes));
    }

    private static SaisieBonCommande.Ligne ligne(String designation, String quantite, String prix) {
        return new SaisieBonCommande.Ligne(designation, null, quantite, "Unité", prix);
    }

    private static Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("u", null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
}
