package afg.achat.afgApprovAchat.service.workflow;

import afg.achat.afgApprovAchat.email.EmailSenderService;
import afg.achat.afgApprovAchat.email.Mail;
import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.CommentaireFinance;
import afg.achat.afgApprovAchat.model.util.EtapeWorkflow;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Règles du workflow de validation des demandes : droits, transitions,
 * contrôles propres à chaque étape, historique et notifications.
 *
 * <p>Chaque action est transactionnelle : tous les contrôles sont faits avant
 * la première écriture, et toute {@link WorkflowException} annule l'ensemble.
 * Les mails ne partent qu'après le commit.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final UtilisateurService utilisateurService;
    private final DemandeMereService demandeMereService;
    private final DemandeFilleService demandeFilleService;
    private final ValidationDemandeService validationDemandeService;
    private final PieceJointeWorkflowService pieceJointeService;
    private final CentreBudgetaireService centreBudgetaireService;
    private final ModeTraitementService modeTraitementService;
    private final CommentaireFinanceService commentaireFinanceService;
    private final EmailSenderService ess;

    // ── Rôles ────────────────────────────────────────────────────────────────

    public static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    public static boolean estBackoffice(Authentication auth) {
        return hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_AUDIT")
                || hasRole(auth, "ROLE_MOYENS_GENERAUX") || hasRole(auth, "ROLE_CONTROLEUR")
                || hasRole(auth, "ROLE_DFC") || hasRole(auth, "ROLE_SG");
    }

    public static boolean hasFinanceAccess(Authentication auth) {
        return hasRole(auth, "ROLE_FINANCE") || hasRole(auth, "ROLE_ADMIN");
    }

    public static boolean hasFinanceReadAccess(Authentication auth) {
        return hasFinanceAccess(auth) || hasRole(auth, "ROLE_FINANCE_CONSULT");
    }

    // ── Droits ───────────────────────────────────────────────────────────────

    public DroitsDecision calculerDroits(DemandeMere demande, Utilisateur current, Authentication auth) {
        Integer demandeurId = demande.getDemandeur() != null ? demande.getDemandeur().getId() : null;
        int statut = demande.getStatut();

        boolean estNPlus1 = demandeurId != null
                && !demandeurId.equals(current.getId())
                && utilisateurService.getIdsUtilisateurVisible(current.getId()).contains(demandeurId);
        boolean estValidateurAssigne = demandeurId != null
                && utilisateurService.getIdsUtilisateursAValider(current.getId()).contains(demandeurId);

        return new DroitsDecision(
                estNPlus1,
                estValidateurAssigne,
                statut == StatutDemande.CREE && (estNPlus1 || estValidateurAssigne),
                peutDeciderAvecRole(auth, statut, EtapeWorkflow.VALIDATION_N1),
                peutDeciderAvecRole(auth, statut, EtapeWorkflow.VALIDATION_N2),
                peutDeciderAvecRole(auth, statut, EtapeWorkflow.VALIDATION_N3),
                peutDeciderAvecRole(auth, statut, EtapeWorkflow.VALIDATION_N4),
                peutDeciderAvecRole(auth, statut, EtapeWorkflow.DECISION_CODEP)
        );
    }

    private static boolean peutDeciderAvecRole(Authentication auth, int statut, EtapeWorkflow etape) {
        return statut == etape.getStatut() && hasRole(auth, "ROLE_" + etape.getRoleValidateur());
    }

    /** Droit de consulter une demande (fiche et pièces jointes). */
    public boolean aAccesDemande(DemandeMere demande, Utilisateur current, Authentication auth) {
        if (estBackoffice(auth)) return true;

        // La finance consulte les paiements directs qui lui ont été transmis
        DemandeMere.StatutTransmissionFinance tf = demande.getStatutTransmissionFinance();
        if (hasFinanceReadAccess(auth)
                && (tf == DemandeMere.StatutTransmissionFinance.TRANSMISE_FINANCE
                || tf == DemandeMere.StatutTransmissionFinance.PAYEE)) {
            return true;
        }

        Integer demandeurId = demande.getDemandeur() != null ? demande.getDemandeur().getId() : null;
        if (demandeurId == null) return false;
        return utilisateurService.getIdsUtilisateurVisible(current.getId()).contains(demandeurId)
                || utilisateurService.getIdsUtilisateursAValider(current.getId()).contains(demandeurId);
    }

    // ── Rejet ────────────────────────────────────────────────────────────────

    @Transactional
    public void rejeter(DemandeMere demande, Utilisateur current, Authentication auth, DecisionDemande cmd) {
        exigerDroitDeDecision(demande, current, auth);

        String cmt = cmd.commentaireNettoye();
        if (cmt.isBlank()) {
            throw new WorkflowException("Le commentaire est obligatoire pour rejeter une demande.");
        }
        pieceJointeService.verifierFormats(cmd.piecesJointes());

        int etape = demande.getStatut();
        pieceJointeService.enregistrerPourDemande(cmd.piecesJointes(), demande, "PJ_REFUS");
        validationDemandeService.logRefus(demande, current, cmt, etape);
        demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.REFUSE);

        apresCommit(() -> envoyerMailRefus(demande, current, cmt, etape));
    }

    // ── Approbation ──────────────────────────────────────────────────────────

    /** Fait passer la demande à l'étape suivante ; renvoie le message de confirmation. */
    @Transactional
    public String approuver(DemandeMere demande, Utilisateur current, Authentication auth, DecisionDemande cmd) {
        DroitsDecision droits = exigerDroitDeDecision(demande, current, auth);
        pieceJointeService.verifierFormats(cmd.piecesJointes());

        EtapeWorkflow etape = EtapeWorkflow.fromStatut(demande.getStatut());

        if (droits.n1() || droits.codep() || droits.mg() || droits.controleur() || droits.dfc() || droits.sg()) {
            // ── 1. Contrôles propres à l'étape (avant toute écriture) ──
            Runnable completerDemande = switch (etape) {
                case VALIDATION_N1 -> preparerValidationMG(demande, current, cmd);
                case VALIDATION_N2 -> preparerValidationControleur(demande, current, cmd);
                default -> () -> { };
            };

            // ── 2. Écritures ──
            List<String> pj = (etape == EtapeWorkflow.DECISION_CODEP)
                    ? pieceJointeService.enregistrerPourCodep(cmd.piecesJointes(), demande, etape.getPrefixePieceJointe())
                    : pieceJointeService.enregistrerPourDemande(cmd.piecesJointes(), demande, etape.getPrefixePieceJointe());
            validationDemandeService.journaliserPiecesJointes(demande, current, etape.getStatut(), pj,
                    etape == EtapeWorkflow.DECISION_CODEP ? "CODEP" : "");

            completerDemande.run();
            passerALEtapeSuivante(demande, current, cmd.commentaireNettoye(), etape);
            return messageApprobation(etape);
        }

        // Admin sans droit propre à l'étape : passage forcé, sans contrôles ni mails
        if (hasRole(auth, "ROLE_ADMIN")) {
            EtapeWorkflow suivante = etape.suivante();
            if (suivante == null) {
                throw new WorkflowException("Cette demande est déjà clôturée.");
            }
            demandeMereService.appliquerDecisionGlobale(demande, suivante.getStatut());
            if (suivante == EtapeWorkflow.VALIDE) {
                demandeMereService.preparerTransmissionFinance(demande);
            }
            validationDemandeService.logValidation(demande, current, cmd.commentaireNettoye(), etape.getStatut());
            return "Statut mis à jour (ADMIN).";
        }

        throw new WorkflowException("Cette demande ne peut pas être traitée par vous.");
    }

    /**
     * Contrôles MG (prix, OPEX/CAPEX, priorité, mode de traitement).
     * Renvoie les modifications à appliquer une fois tous les contrôles passés.
     */
    private Runnable preparerValidationMG(DemandeMere demande, Utilisateur current, DecisionDemande cmd) {
        verifierPrixLignes(demande, "Impossible de valider");
        DemandeMere.NatureDemande nature = parseNature(cmd.typeDemande());
        DemandeMere.PrioriteDemande priorite = parsePriorite(cmd.priorite());
        ModeTraitement mode = parseModeTraitement(cmd.modeTraitement());

        return () -> {
            demande.setNatureDemande(nature);
            appliquerPriorite(demande, current, priorite);
            demande.setModeTraitement(mode);
            validationDemandeService.journaliser(demande, current, demande.getStatut(),
                    "Ajout du mode de traitement : " + mode.getLibelle());
            demandeMereService.recalculerTotal(demande);
        };
    }

    /** Contrôles du contrôleur de gestion (ligne budgétaire et commentaire obligatoires). */
    private Runnable preparerValidationControleur(DemandeMere demande, Utilisateur current, DecisionDemande cmd) {
        String ligne = cmd.ligneBudgetaire();
        if (ligne == null || ligne.isBlank()) {
            throw new WorkflowException("Ligne budgétaire obligatoire pour le contrôleur de gestion.");
        }
        String commentaire = cmd.commentaireControleur();
        if (commentaire == null || commentaire.isBlank()) {
            throw new WorkflowException("Le commentaire du contrôleur de gestion est obligatoire.");
        }
        CentreBudgetaire centre = null;
        try {
            centre = centreBudgetaireService.getCentreBudgetaireById(Integer.parseInt(ligne.trim()));
        } catch (NumberFormatException ignored) {
            // centre reste null
        }
        if (centre == null) {
            throw new WorkflowException("Ligne budgétaire invalide : " + ligne);
        }

        CentreBudgetaire centreRetenu = centre;
        return () -> {
            demande.setCentreBudgetaire(centreRetenu);
            CommentaireFinance cf = new CommentaireFinance();
            cf.setCommentateur(current);
            cf.setDemandeMere(demande);
            cf.setCommentaire(commentaire.trim());
            commentaireFinanceService.insertCommentaireFinance(cf);
        };
    }

    private void passerALEtapeSuivante(DemandeMere demande, Utilisateur current, String cmt, EtapeWorkflow etape) {
        EtapeWorkflow suivante = etape.suivante();
        demandeMereService.appliquerDecisionGlobale(demande, suivante.getStatut());
        if (suivante == EtapeWorkflow.VALIDE) {
            demandeMereService.preparerTransmissionFinance(demande);
        }
        validationDemandeService.logValidation(demande, current, cmt, etape.getStatut());

        apresCommit(() -> {
            ess.envoyerMailDemandeur(demande, current, cmt, etape.getStatut(), suivante.getStatut());
            if (suivante == EtapeWorkflow.VALIDE) {
                ess.envoyerMailValidateur(demande, current, etape.getStatut(), suivante.getStatut(), null);
            } else {
                for (Utilisateur u : utilisateurService.getUtilisateursByRole(suivante.getRoleValidateur())) {
                    ess.envoyerMailValidateur(demande, current, etape.getStatut(), suivante.getStatut(), u);
                }
            }
        });
    }

    private static String messageApprobation(EtapeWorkflow etape) {
        return switch (etape) {
            case CREE           -> "Demande envoyée en validation N1 (MG).";
            case VALIDATION_N1  -> "Demande validée par les Moyens Généraux (N2).";
            case DECISION_CODEP -> "Demande validée par le Comité de dépense.";
            case VALIDATION_N2  -> "Demande validée par le contrôleur de gestion (N3).";
            case VALIDATION_N3  -> "Demande validée par la D.F.C., transmise au S.G.";
            case VALIDATION_N4  -> "Demande validée et finalisée par le S.G.";
            default             -> "Demande validée.";
        };
    }

    // ── Envoi au CODEP ───────────────────────────────────────────────────────

    @Transactional
    public void envoyerAuCodep(DemandeMere demande, Utilisateur current, Authentication auth,
                               String typeDemande, String prioriteParam, MultipartFile[] piecesJointes) {
        if (!calculerDroits(demande, current, auth).mg()) {
            throw new WorkflowException("Vous ne pouvez pas envoyer cette demande au CODEP.");
        }

        // ── 1. Contrôles ──
        verifierPrixLignes(demande, "Impossible d'envoyer au CODEP");
        pieceJointeService.verifierFormats(piecesJointes);
        DemandeMere.NatureDemande nature = parseNature(typeDemande);
        DemandeMere.PrioriteDemande priorite = parsePriorite(prioriteParam);

        // ── 2. Écritures ──
        int etape = demande.getStatut();
        demande.setNatureDemande(nature);
        appliquerPriorite(demande, current, priorite);
        demande.setViaCodep(true);

        List<String> pj = pieceJointeService.enregistrerPourCodep(piecesJointes, demande, "PJ_CODEP");
        validationDemandeService.journaliserPiecesJointes(demande, current, etape, pj, "");

        demandeMereService.recalculerTotal(demande);
        demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.DECISION_CODEP);
        validationDemandeService.logValidation(demande, current, "Envoi au CODEP", etape);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private DroitsDecision exigerDroitDeDecision(DemandeMere demande, Utilisateur current, Authentication auth) {
        DroitsDecision droits = calculerDroits(demande, current, auth);
        if (!droits.auMoinsUn() && !hasRole(auth, "ROLE_ADMIN")) {
            throw new WorkflowException("Cette demande ne peut pas être traitée par vous.");
        }
        return droits;
    }

    private void verifierPrixLignes(DemandeMere demande, String prefixeMessage) {
        List<String> sansPrix = demandeFilleService.getDemandeFilleByDemandeMere(demande).stream()
                .filter(l -> l.getStatut() != StatutDemande.REFUSE)
                .filter(l -> l.getPrixUnitaire() == null || l.getPrixUnitaire() <= 0)
                .map(WorkflowService::libelleLigne)
                .toList();
        if (!sansPrix.isEmpty()) {
            throw new WorkflowException(prefixeMessage + " : les articles suivants n'ont pas de prix unitaire : "
                    + String.join(", ", sansPrix));
        }
    }

    private static String libelleLigne(DemandeFille l) {
        return l.getArticle() != null
                ? l.getArticle().getCodeArticle() + " - " + l.getArticle().getDesignation()
                : "Article inconnu";
    }

    private static DemandeMere.NatureDemande parseNature(String typeDemande) {
        String td = (typeDemande == null) ? "" : typeDemande.trim().toUpperCase();
        if (td.isBlank()) {
            throw new WorkflowException("Veuillez choisir le Type de demande (OPEX/CAPEX) avant de valider.");
        }
        try {
            return DemandeMere.NatureDemande.valueOf(td);
        } catch (IllegalArgumentException ex) {
            throw new WorkflowException("Type de demande invalide : " + typeDemande);
        }
    }

    /** Priorité facultative : {@code null} si non fournie. */
    private static DemandeMere.PrioriteDemande parsePriorite(String priorite) {
        if (priorite == null || priorite.isBlank()) return null;
        try {
            return DemandeMere.PrioriteDemande.valueOf(priorite.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WorkflowException("Priorité invalide : " + priorite);
        }
    }

    private ModeTraitement parseModeTraitement(String modeTraitement) {
        if (modeTraitement == null || modeTraitement.isBlank()) {
            throw new WorkflowException("Le mode de traitement est obligatoire.");
        }
        ModeTraitement mt = null;
        try {
            mt = modeTraitementService.getModeTraitementById(Integer.parseInt(modeTraitement.trim()));
        } catch (NumberFormatException ignored) {
            // mt reste null
        }
        if (mt == null) {
            throw new WorkflowException("Mode de traitement invalide : " + modeTraitement);
        }
        return mt;
    }

    /** Change la priorité (sans sauvegarder) et l'historise si elle a changé. */
    private void appliquerPriorite(DemandeMere demande, Utilisateur auteur, DemandeMere.PrioriteDemande nouvelle) {
        if (nouvelle == null || nouvelle == demande.getPriorite()) return;
        String ancienne = demande.getPriorite() != null ? demande.getPriorite().name() : "N/A";
        demande.setPriorite(nouvelle);
        validationDemandeService.journaliser(demande, auteur, demande.getStatut(),
                "Modification de priorité : " + ancienne + " → " + nouvelle.name());
    }

    private void envoyerMailRefus(DemandeMere demande, Utilisateur validateur, String cmt, int etape) {
        Map<String, Object> props = new HashMap<>();
        props.put("id",           demande.getId());
        props.put("demandeur",    demande.getDemandeur());
        props.put("validateur",   validateur);
        props.put("commentaire",  cmt);
        props.put("dateDecision", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        props.put("etape",        StatutDemande.getLibelle(etape));

        ess.sendEmail(new Mail(
                "refusDemande",
                demande.getDemandeur().getMail(),
                "[AFG/MADA] - Votre demande a été refusée",
                props
        ));
    }

    /** Exécute l'action après le commit de la transaction courante (ou tout de suite s'il n'y en a pas). */
    private static void apresCommit(Runnable action) {
        Runnable sansEchec = () -> {
            try {
                action.run();
            } catch (Exception e) {
                log.error("Échec de l'envoi des notifications du workflow", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sansEchec.run();
                }
            });
        } else {
            sansEchec.run();
        }
    }
}
