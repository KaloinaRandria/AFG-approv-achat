package afg.achat.afgApprovAchat.model.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EtapeWorkflowTest {

    @Test
    void transitionsDuCircuitNormal() {
        assertEquals(EtapeWorkflow.VALIDATION_N1, EtapeWorkflow.CREE.suivante());
        assertEquals(EtapeWorkflow.VALIDATION_N2, EtapeWorkflow.VALIDATION_N1.suivante());
        assertEquals(EtapeWorkflow.VALIDATION_N3, EtapeWorkflow.VALIDATION_N2.suivante());
        assertEquals(EtapeWorkflow.VALIDATION_N4, EtapeWorkflow.VALIDATION_N3.suivante());
        assertEquals(EtapeWorkflow.VALIDE, EtapeWorkflow.VALIDATION_N4.suivante());
        assertNull(EtapeWorkflow.VALIDE.suivante());
        assertNull(EtapeWorkflow.REFUSE.suivante());
    }

    @Test
    void codepReprendAuControleur() {
        assertEquals(EtapeWorkflow.VALIDATION_N2, EtapeWorkflow.DECISION_CODEP.suivante());
    }

    @Test
    void rolesNotifiesALEtapeSuivante() {
        assertEquals("MOYENS_GENERAUX", EtapeWorkflow.CREE.suivante().getRoleValidateur());
        assertEquals("CONTROLEUR", EtapeWorkflow.VALIDATION_N1.suivante().getRoleValidateur());
        assertEquals("CONTROLEUR", EtapeWorkflow.DECISION_CODEP.suivante().getRoleValidateur());
        assertEquals("DFC", EtapeWorkflow.VALIDATION_N2.suivante().getRoleValidateur());
        assertEquals("SG", EtapeWorkflow.VALIDATION_N3.suivante().getRoleValidateur());
    }

    @Test
    void numerotationIdentiqueAAvantRefactoring() {
        // Circuit normal
        assertEquals(1, EtapeWorkflow.numeroDansCircuit(StatutDemande.CREE, false));
        assertEquals(2, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N1, false));
        assertEquals(3, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N2, false));
        assertEquals(4, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N3, false));
        assertEquals(5, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N4, false));
        assertEquals(6, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDE, false));
        assertEquals(-1, EtapeWorkflow.numeroDansCircuit(StatutDemande.REFUSE, false));
        assertEquals(1, EtapeWorkflow.numeroDansCircuit(StatutDemande.DECISION_CODEP, false));
        assertEquals(1, EtapeWorkflow.numeroDansCircuit(999, false));

        // Circuit CODEP
        assertEquals(1, EtapeWorkflow.numeroDansCircuit(StatutDemande.CREE, true));
        assertEquals(2, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N1, true));
        assertEquals(3, EtapeWorkflow.numeroDansCircuit(StatutDemande.DECISION_CODEP, true));
        assertEquals(4, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N2, true));
        assertEquals(5, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N3, true));
        assertEquals(6, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N4, true));
        assertEquals(7, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDE, true));
    }

    @Test
    void fraiseAutantDeLibellesQueDEtapes() {
        assertEquals(EtapeWorkflow.circuit(false).size(), EtapeWorkflow.libellesCircuit(false).size());
        assertEquals(EtapeWorkflow.circuit(true).size(), EtapeWorkflow.libellesCircuit(true).size());
    }

    // ── Type de demande ──────────────────────────────────────────────────────

    @Test
    void fournitureSArreteAuControleur() {
        TypeDemande f = TypeDemande.FOURNITURE;
        assertEquals(EtapeWorkflow.VALIDATION_N1, EtapeWorkflow.CREE.suivante(f));
        assertEquals(EtapeWorkflow.VALIDATION_N2, EtapeWorkflow.VALIDATION_N1.suivante(f));
        assertEquals(EtapeWorkflow.VALIDATION_N2, EtapeWorkflow.DECISION_CODEP.suivante(f));
        assertEquals(EtapeWorkflow.VALIDE, EtapeWorkflow.VALIDATION_N2.suivante(f));
        assertNull(EtapeWorkflow.VALIDE.suivante(f));
        assertNull(EtapeWorkflow.REFUSE.suivante(f));
    }

    @Test
    void typeStandardOuAbsentSuitLeCircuitComplet() {
        for (EtapeWorkflow e : EtapeWorkflow.values()) {
            assertEquals(e.suivante(), e.suivante(TypeDemande.STANDARD), e.name());
            assertEquals(e.suivante(), e.suivante(null), e.name());
        }
        assertEquals(EtapeWorkflow.circuit(false), EtapeWorkflow.circuit(false, null));
        assertEquals(EtapeWorkflow.circuit(true), EtapeWorkflow.circuit(true, TypeDemande.STANDARD));
        assertEquals(EtapeWorkflow.libellesCircuit(true), EtapeWorkflow.libellesCircuit(true, null));
    }

    @Test
    void circuitEtFriseDUneFourniture() {
        TypeDemande f = TypeDemande.FOURNITURE;
        assertEquals(List.of(EtapeWorkflow.CREE, EtapeWorkflow.VALIDATION_N1, EtapeWorkflow.VALIDATION_N2,
                EtapeWorkflow.VALIDE), EtapeWorkflow.circuit(false, f));
        assertEquals(List.of(EtapeWorkflow.CREE, EtapeWorkflow.VALIDATION_N1, EtapeWorkflow.DECISION_CODEP,
                EtapeWorkflow.VALIDATION_N2, EtapeWorkflow.VALIDE), EtapeWorkflow.circuit(true, f));

        assertEquals(List.of("Demande créée", "Supérieur hiérarchique (N+1)", "Moyens Généraux",
                "Contrôleur de gestion"), EtapeWorkflow.libellesCircuit(false, f));
        assertEquals(List.of("Demande créée", "Supérieur hiérarchique (N+1)", "Moyens Généraux",
                "Comité de dépense", "Contrôleur de gestion"), EtapeWorkflow.libellesCircuit(true, f));

        assertEquals(3, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDATION_N2, false, f));
        assertEquals(4, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDE, false, f));
        assertEquals(5, EtapeWorkflow.numeroDansCircuit(StatutDemande.VALIDE, true, f));
        assertEquals(-1, EtapeWorkflow.numeroDansCircuit(StatutDemande.REFUSE, false, f));

        assertEquals("Contrôleur de gestion",
                EtapeWorkflow.libellesEtapeFranchie(false, f).get(StatutDemande.VALIDE));
    }

    @Test
    void chaqueStatutALibelleBadgeEtIcone() {
        for (EtapeWorkflow e : EtapeWorkflow.values()) {
            assertNotNull(EtapeWorkflow.libellesAttente().get(e.getStatut()), e.name());
            assertNotNull(EtapeWorkflow.badgesListe().get(e.getStatut()), e.name());
            assertNotNull(EtapeWorkflow.badgesFiche().get(e.getStatut()), e.name());
            assertNotNull(EtapeWorkflow.icones().get(e.getStatut()), e.name());
            assertEquals(e, EtapeWorkflow.fromStatut(e.getStatut()));
        }
    }

    @Test
    void libelleEtapeFranchieVersValideDependDuCodep() {
        assertEquals("S.G.", EtapeWorkflow.libellesEtapeFranchie(false).get(StatutDemande.VALIDE));
        assertEquals("Comité de dépense", EtapeWorkflow.libellesEtapeFranchie(true).get(StatutDemande.VALIDE));
    }
}
