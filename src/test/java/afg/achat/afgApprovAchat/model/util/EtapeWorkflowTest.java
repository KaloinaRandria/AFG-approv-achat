package afg.achat.afgApprovAchat.model.util;

import org.junit.jupiter.api.Test;

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
