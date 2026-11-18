package afg.achat.afgApprovAchat.service.bonlivraison;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutBonCommande;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere.StatutReception;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeFilleRepo;
import afg.achat.afgApprovAchat.repository.bonCommande.BonCommandeMereRepo;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonFilleRepo;
import afg.achat.afgApprovAchat.repository.bonLivraison.BonLivraisonMereRepo;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.bonlivraison.ReceptionBonCommandeService.LigneSaisie;
import afg.achat.afgApprovAchat.service.bonlivraison.ReceptionBonCommandeService.SaisieReception;
import afg.achat.afgApprovAchat.service.stock.LotStockService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.util.PrixArticleService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceptionBonCommandeServiceTest {

    @Mock BonCommandeMereRepo bonCommandeMereRepo;
    @Mock BonCommandeFilleRepo bonCommandeFilleRepo;
    @Mock BonLivraisonMereRepo bonLivraisonMereRepo;
    @Mock BonLivraisonFilleRepo bonLivraisonFilleRepo;
    @Mock ArticleService articleService;
    @Mock DeviseService deviseService;
    @Mock PrixArticleService prixArticleService;
    @Mock LotStockService lotStockService;
    @Mock StockMereService stockMereService;
    @Mock IdGenerator idGenerator;

    @InjectMocks ReceptionBonCommandeService service;

    private static final String DATE = LocalDateTime.now().minusHours(1).withNano(0).toString();

    private BonCommandeMere bc;
    private BonCommandeFille ligneA;
    private BonCommandeFille ligneB;
    private Article stylo;
    private Fournisseur fournisseur;

    @BeforeEach
    void setUp() {
        when(idGenerator.generateId(any(), any())).thenReturn("BC1");
        bc = new BonCommandeMere();
        bc.setId(idGenerator);
        bc.setNumero("18112026-1");
        bc.setStatut(StatutBonCommande.VALIDE);
        fournisseur = new Fournisseur();
        fournisseur.setId(7);
        bc.setFournisseur(fournisseur);

        stylo = new Article();
        stylo.setCodeArticle("ART1");
        stylo.setDesignation("Stylo");

        ligneA = ligne(1, stylo, 10, 500);
        ligneB = ligne(2, stylo, 4, 500);   // même article sur une deuxième ligne (autre demande)

        when(bonCommandeMereRepo.verrouiller("BC1")).thenReturn(Optional.of(bc));
        when(bonCommandeFilleRepo.findBonCommandeFillesByBonCommandeMere(bc)).thenReturn(List.of(ligneA, ligneB));
        Devise mga = new Devise();
        when(deviseService.getDeviseById(1)).thenReturn(Optional.of(mga));
        when(stockMereService.getOrCreateStockMere(any(BonLivraisonMere.class))).thenReturn(new StockMere());
        when(idGenerator.generateId(eq("BL"), any())).thenReturn("BL1");
    }

    private static BonCommandeFille ligne(int id, Article article, double qte, double prix) {
        BonCommandeFille l = new BonCommandeFille();
        l.setId(id);
        l.setArticle(article);
        l.setQuantiteCommandee(qte);
        l.setQuantiteRestante(qte);
        l.setPrixUnitaireHT(prix);
        return l;
    }

    private SaisieReception saisie(Map<Integer, LigneSaisie> lignes) {
        return new SaisieReception("1", DATE, "", lignes);
    }

    @Test
    void receptionPartielleRattacheLeBlEtMetAJourLeReste() {
        BonLivraisonMere bl = service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("6", "500", null))));

        assertSame(bc, bl.getBonCommandeMere());
        assertSame(fournisseur, bl.getFournisseur());
        assertEquals(3000, bl.getTotalPrix());
        assertEquals(6, ligneA.getQuantiteRecue());
        assertEquals(4, ligneA.getQuantiteRestante());
        assertEquals(StatutReception.PARTIELLEMENT_RECU, bc.getStatutReception());

        ArgumentCaptor<BonLivraisonFille> blf = ArgumentCaptor.forClass(BonLivraisonFille.class);
        verify(bonLivraisonFilleRepo).save(blf.capture());
        assertSame(ligneA, blf.getValue().getBonCommandeFille());
        assertEquals(10, blf.getValue().getQuantiteDemande());
        verify(lotStockService).creerLot(stylo, bl, 6, 500);
        verify(stockMereService).addEntree(any(), eq(stylo), eq(6.0));
    }

    @Test
    void receptionDuResteSoldeLeBon() {
        ligneA.setQuantiteRecue(6);
        service.receptionner("BC1", saisie(Map.of(
                1, new LigneSaisie("4", "500", null),
                2, new LigneSaisie("4", "520", null))));

        assertEquals(0, ligneA.getResteARecevoir());
        assertEquals(0, ligneB.getResteARecevoir());
        assertEquals(StatutReception.RECU, bc.getStatutReception());
    }

    @Test
    void quantiteSuperieureAuResteRefusee() {
        ligneA.setQuantiteRecue(8);
        WorkflowException e = assertThrows(WorkflowException.class,
                () -> service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("3", "500", null)))));
        assertTrue(e.getMessage().contains("dépasse le reste"));
        verifyNoInteractions(bonLivraisonMereRepo, lotStockService);
    }

    @Test
    void aucuneQuantiteRefusee() {
        assertThrows(WorkflowException.class,
                () -> service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("0", "500", null)))));
        verify(bonLivraisonMereRepo, never()).save(any());
    }

    @Test
    void bonNonValideRefuse() {
        bc.setStatut(StatutBonCommande.EN_ATTENTE_VALIDATION);
        assertThrows(WorkflowException.class,
                () -> service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("1", "500", null)))));
    }

    @Test
    void bonSoldeRefuse() {
        bc.setStatutReception(StatutReception.SOLDE);
        assertThrows(WorkflowException.class,
                () -> service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("1", "500", null)))));
    }

    @Test
    void ligneSansArticleExigeUnArticleActif() {
        ligneA.setArticle(null);
        ligneA.setDesignation("Rame de papier A4");
        assertThrows(WorkflowException.class,
                () -> service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("2", "500", "")))));

        Article papier = new Article();
        papier.setCodeArticle("ART2");
        when(articleService.getArticleByCodeArticle("ART2")).thenReturn(Optional.of(papier));
        BonLivraisonMere bl = service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("2", "500", "ART2"))));
        verify(lotStockService).creerLot(papier, bl, 2, 500);
    }

    @Test
    void articleInactifRefuse() {
        stylo.setActif(false);
        assertThrows(WorkflowException.class,
                () -> service.receptionner("BC1", saisie(Map.of(1, new LigneSaisie("1", "500", null)))));
    }

    @Test
    void dateFutureRefusee() {
        String demain = LocalDateTime.now().plusDays(1).withNano(0).toString();
        assertThrows(WorkflowException.class, () -> service.receptionner("BC1",
                new SaisieReception("1", demain, "", Map.of(1, new LigneSaisie("1", "500", null)))));
    }

    @Test
    void solderExigeUneReceptionPartielleEtUnMotif() {
        Utilisateur u = new Utilisateur();
        assertThrows(WorkflowException.class, () -> service.solderReliquat("BC1", "rupture", u));

        bc.setStatutReception(StatutReception.PARTIELLEMENT_RECU);
        assertThrows(WorkflowException.class, () -> service.solderReliquat("BC1", " ", u));

        service.solderReliquat("BC1", "rupture fournisseur", u);
        assertEquals(StatutReception.SOLDE, bc.getStatutReception());
        assertEquals("rupture fournisseur", bc.getMotifClotureReception());
        assertSame(u, bc.getClotureReceptionPar());
    }

    @Test
    void ancienBonSansStatutEstNonRecuEtReceptionnable() {
        assertEquals(StatutReception.NON_RECU, bc.getStatutReception());
        assertTrue(bc.estReceptionnable());
    }
}
