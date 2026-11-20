package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieMereRepo;
import afg.achat.afgApprovAchat.repository.stock.StockFilleRepo;
import afg.achat.afgApprovAchat.repository.stock.StockMereRepo;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.stock.LotStockService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonSortieServiceTest {

    @Mock BonSortieMereRepo bsMereRepo;
    @Mock BonSortieFilleRepo bsFilleRepo;
    @Mock DemandeFilleService demandeFilleService;
    @Mock DemandeMereService demandeMereService;
    @Mock StockFilleRepo stockFilleRepo;
    @Mock StockMereRepo stockMereRepo;
    @Mock LotStockService lotStockService;

    @InjectMocks BonSortieService service;

    private DemandeMere demande;
    private final Utilisateur mg = new Utilisateur();
    private final List<BonSortieFille> lignesEnregistrees = new ArrayList<>();
    private BonSortieMere bsEnregistre;

    @BeforeEach
    void setUp() {
        demande = new DemandeMere();
        var idGenerator = mock(IdGenerator.class);
        when(idGenerator.generateId(any(), any())).thenReturn("DM1");
        demande.setId(idGenerator);

        // Les bons et leurs lignes sont « enregistrés » dans des listes en mémoire
        when(bsMereRepo.save(any())).thenAnswer(i -> {
            bsEnregistre = i.getArgument(0);
            return bsEnregistre;
        });
        when(bsMereRepo.findById(anyInt())).thenAnswer(i -> Optional.ofNullable(bsEnregistre));
        when(bsFilleRepo.save(any())).thenAnswer(i -> {
            lignesEnregistrees.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(bsFilleRepo.findByBonSortieMere(any())).thenAnswer(i -> lignesEnregistrees);
        when(bsFilleRepo.sumQuantiteSortieByDemandeFille(any())).thenReturn(0.0);
        when(stockMereRepo.findByDemandeMere(any())).thenReturn(Optional.of(new StockMere()));
        when(stockFilleRepo.findByStockMereAndArticle(any(), any())).thenReturn(Optional.empty());
        when(demandeFilleService.getDemandeFilleByDemandeMere(any())).thenReturn(List.of());
    }

    @Test
    void lignesSansQuantiteIgnoreesEtChaqueQuantiteSurSaLigne() {
        // Cas du bug : la ligne 11 n'a pas de stock (champ non envoyé ou à 0)
        ligne(10, "ART1", 5, 100);
        ligne(11, "ART2", 3, 0);
        ligne(12, "ART3", 4, 100);

        Map<Integer, Double> quantites = new LinkedHashMap<>();
        quantites.put(10, 2.0);
        quantites.put(11, 0.0);
        quantites.put(12, 4.0);

        BonSortieMere bs = service.creerEtConfirmer(demande, mg, " urgent ", quantites);

        assertEquals(2, lignesEnregistrees.size());
        assertEquals("ART1", lignesEnregistrees.get(0).getArticle().getCodeArticle());
        assertEquals(2.0, lignesEnregistrees.get(0).getQuantiteSortie());
        assertEquals("ART3", lignesEnregistrees.get(1).getArticle().getCodeArticle());
        assertEquals(4.0, lignesEnregistrees.get(1).getQuantiteSortie());
        assertEquals(BonSortieMere.StatutBonSortie.CONFIRME, bs.getStatut());
        assertEquals("urgent", bs.getCommentaire());
        verify(lotStockService).consommerStock("ART1", 2.0);
        verify(lotStockService).consommerStock("ART3", 4.0);
        verify(lotStockService, never()).consommerStock(eq("ART2"), anyDouble());
    }

    @Test
    void aucuneQuantitePositiveRienNEstEnregistre() {
        ligne(10, "ART1", 5, 100);
        assertThrows(IllegalArgumentException.class,
                () -> service.creerEtConfirmer(demande, mg, null, Map.of(10, 0.0)));
        verify(bsMereRepo, never()).save(any());
    }

    @Test
    void stockInsuffisantRefuseAvantToutEnregistrement() {
        ligne(10, "ART1", 5, 1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.creerEtConfirmer(demande, mg, null, Map.of(10, 3.0)));
        assertTrue(e.getMessage().startsWith("Stock insuffisant"));
        verify(bsMereRepo, never()).save(any());
        verify(lotStockService, never()).consommerStock(any(), anyDouble());
    }

    @Test
    void ligneDUneAutreDemandeRefusee() {
        DemandeFille autre = ligne(10, "ART1", 5, 100);
        DemandeMere autreDemande = new DemandeMere();
        var idGenerator = mock(IdGenerator.class);
        when(idGenerator.generateId(any(), any())).thenReturn("DM2");
        autreDemande.setId(idGenerator);
        autre.setDemandeMere(autreDemande);

        assertThrows(IllegalArgumentException.class,
                () -> service.creerEtConfirmer(demande, mg, null, Map.of(10, 1.0)));
        verify(bsMereRepo, never()).save(any());
    }

    @Test
    void quantiteSuperieureAuRestantRefusee() {
        ligne(10, "ART1", 5, 100);
        when(bsFilleRepo.sumQuantiteSortieByDemandeFille(any())).thenReturn(4.0);
        assertThrows(IllegalArgumentException.class,
                () -> service.creerEtConfirmer(demande, mg, null, Map.of(10, 2.0)));
        verify(lotStockService, never()).consommerStock(any(), anyDouble());
    }

    @Test
    void lesBrouillonsNeSontPasAffiches() {
        BonSortieMere brouillon = new BonSortieMere();
        brouillon.setStatut(BonSortieMere.StatutBonSortie.BROUILLON);
        BonSortieMere confirme = new BonSortieMere();
        confirme.setStatut(BonSortieMere.StatutBonSortie.CONFIRME);
        when(bsMereRepo.findByDemandeMere(demande)).thenReturn(List.of(brouillon, confirme));

        assertEquals(List.of(confirme), service.getBonSortieByDemande(demande));
    }

    @Test
    void laListeExclutLesBrouillonsEtCouvreToutLeDernierJour() {
        when(bsMereRepo.rechercher(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);

        // Sans statut, ou statut BROUILLON demandé : confirmés et annulés seulement
        service.rechercher(null, null, null, null, null, null, pageable);
        service.rechercher(BonSortieMere.StatutBonSortie.BROUILLON, null, null, null, null, null, pageable);
        verify(bsMereRepo, times(2)).rechercher(eq(BonSortieService.STATUTS_LISTE),
                any(), any(), eq(""), eq(""), eq(""), eq(pageable));

        // Période : du début du premier jour à la fin du dernier ; textes nettoyés
        service.rechercher(BonSortieMere.StatutBonSortie.CONFIRME,
                java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 18),
                " BS-DM1 ", null, "ART1", pageable);
        verify(bsMereRepo).rechercher(eq(List.of(BonSortieMere.StatutBonSortie.CONFIRME)),
                eq(java.time.LocalDateTime.of(2026, 11, 1, 0, 0)),
                eq(java.time.LocalDate.of(2026, 11, 18).atTime(java.time.LocalTime.MAX)),
                eq("BS-DM1"), eq(""), eq("ART1"), eq(pageable));
    }

    @Test
    void annulationRemetEnStockEtRecalculeLaLivraison() {
        BonSortieMere bs = bonConfirme();
        Article article = new Article();
        article.setCodeArticle("ART1");
        BonSortieFille l = new BonSortieFille();
        l.setArticle(article);
        l.setQuantiteSortie(3.0);
        l.setPrixUnitaire(250.0);
        lignesEnregistrees.add(l);

        StockFille sf = new StockFille();
        sf.setSortie("5.0");
        when(stockFilleRepo.findByStockMereAndArticle(any(), eq(article))).thenReturn(Optional.of(sf));

        BonSortieMere annule = service.annulerBonSortie(1, mg, "  erreur de quantité ");

        assertEquals(BonSortieMere.StatutBonSortie.ANNULE, annule.getStatut());
        assertEquals("erreur de quantité", annule.getMotifAnnulation());
        assertSame(mg, annule.getAnnulePar());
        assertNotNull(annule.getDateAnnulation());
        assertEquals(2.0, sf.getSortie());
        // Lot recréé au prix de la ligne, daté de la sortie annulée
        verify(lotStockService).restituerStock(article, 3.0, 250.0, bs.getDateConfirmation());
        verify(demandeMereService).saveDemandeMere(demande);
    }

    @Test
    void annulationSansMotifRefusee() {
        bonConfirme();
        assertThrows(IllegalArgumentException.class, () -> service.annulerBonSortie(1, mg, "  "));
        verify(lotStockService, never()).restituerStock(any(), anyDouble(), anyDouble(), any());
    }

    @Test
    void seulUnBonConfirmePeutEtreAnnule() {
        BonSortieMere bs = bonConfirme();
        bs.setStatut(BonSortieMere.StatutBonSortie.ANNULE);
        assertThrows(IllegalStateException.class, () -> service.annulerBonSortie(1, mg, "doublon"));
        verify(lotStockService, never()).restituerStock(any(), anyDouble(), anyDouble(), any());
        verify(bsMereRepo, never()).save(any());
    }

    private BonSortieMere bonConfirme() {
        BonSortieMere bs = new BonSortieMere();
        bs.setId(1);
        bs.setNumeroBs("BS-DM1-1");
        bs.setDemandeMere(demande);
        bs.setStatut(BonSortieMere.StatutBonSortie.CONFIRME);
        bs.setDateConfirmation(java.time.LocalDateTime.of(2026, 11, 18, 10, 0));
        when(bsMereRepo.findByIdPourMiseAJour(1)).thenReturn(Optional.of(bs));
        return bs;
    }

    private DemandeFille ligne(int id, String codeArticle, double quantite, double stock) {
        Article article = new Article();
        article.setCodeArticle(codeArticle);
        article.setDesignation("Article " + codeArticle);

        DemandeFille df = new DemandeFille();
        df.setId(id);
        df.setArticle(article);
        df.setQuantite(String.valueOf(quantite));
        df.setPrixUnitaire(1000.0);
        df.setDemandeMere(demande);

        when(demandeFilleService.getDemandeFilleById(id)).thenReturn(df);
        when(lotStockService.getStockDisponible(codeArticle)).thenReturn(stock);
        return df;
    }
}
