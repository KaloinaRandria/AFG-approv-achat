package afg.achat.afgApprovAchat.service.utilisateur;

import afg.achat.afgApprovAchat.DTO.UtilisateurRequestDTO;
import afg.achat.afgApprovAchat.model.utilisateur.DemandeCompte;
import afg.achat.afgApprovAchat.model.utilisateur.DemandeCompte.Statut;
import afg.achat.afgApprovAchat.model.utilisateur.DemandeCompte.TypeDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Role;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.util.RoleRepo;
import afg.achat.afgApprovAchat.repository.util.ServiceRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.DemandeCompteRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.PosteRepo;
import afg.achat.afgApprovAchat.service.securite.Habilitations;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import afg.achat.afgApprovAchat.email.EmailSenderService;
import afg.achat.afgApprovAchat.email.Mail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Circuit « créateur / valideur » des comptes utilisateurs (domaine {@link Habilitations#IT}).
 *
 * <p>Un créateur (IT_MAKER) soumet une création, une modification ou une (dés)activation :
 * rien n'est appliqué tant qu'une autre personne (IT_CHECKER) ne l'a pas validée. Le valideur
 * peut aussi la rejeter avec un motif ; le créateur peut annuler sa demande tant qu'elle attend.
 * Un compte n'a qu'une demande en attente à la fois. L'administrateur applique directement.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandeCompteService {

    private final DemandeCompteRepo demandeCompteRepo;
    private final UtilisateurService utilisateurService;
    private final RoleRepo roleRepo;
    private final PosteRepo posteRepo;
    private final ServiceRepo serviceRepo;
    private final ObjectMapper objectMapper;
    private final EmailSenderService emailSenderService;

    /** Une différence entre l'état actuel du compte et la demande (écran de validation). */
    public record Changement(String libelle, String avant, String apres) {
        // Accesseurs de type « getter » pour les gabarits (c.libelle, c.avant, c.apres)
        public String getLibelle() { return libelle; }
        public String getAvant()   { return avant; }
        public String getApres()   { return apres; }
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    public List<DemandeCompte> enAttente() {
        return demandeCompteRepo.findByStatutOrderByDateDemandeAsc(Statut.EN_ATTENTE);
    }

    public List<DemandeCompte> historique() {
        return demandeCompteRepo.findTop30ByStatutNotOrderByDateDecisionDesc(Statut.EN_ATTENTE);
    }

    public long nombreEnAttente() {
        return demandeCompteRepo.countByStatut(Statut.EN_ATTENTE);
    }

    /** Comptes qui ont une demande en attente. */
    public Set<Integer> ciblesEnAttente() {
        return new HashSet<>(demandeCompteRepo.idsCiblesParStatut(Statut.EN_ATTENTE));
    }

    /** Demandes en attente que l'utilisateur peut valider (hors les siennes) : [nombre, plus ancienne date]. */
    public Object[] aValiderPar(Utilisateur utilisateur) {
        List<Object[]> r = demandeCompteRepo.compterAValiderPar(Statut.EN_ATTENTE, utilisateur.getId());
        return r.isEmpty() ? new Object[]{0L, null} : r.get(0);
    }

    // ── Soumission (créateur) ────────────────────────────────────────────────

    /** @throws IllegalArgumentException si l'email est déjà pris ou demandé dans une autre création en attente */
    @Transactional
    public DemandeCompte soumettreCreation(UtilisateurRequestDTO dto, Utilisateur demandeur) {
        verifierSaisie(dto);
        if (utilisateurService.getUtilisateurByMail(dto.getMail()) != null) {
            throw new IllegalArgumentException("Un utilisateur avec le mail '" + dto.getMail() + "' existe déjà.");
        }
        boolean dejaDemande = enAttente().stream()
                .filter(d -> d.getType() == TypeDemande.CREATION)
                .map(this::lireDonnees)
                .anyMatch(x -> x != null && dto.getMail().equalsIgnoreCase(x.getMail()));
        if (dejaDemande) {
            throw new IllegalArgumentException("Une création de compte pour '" + dto.getMail() + "' attend déjà une validation.");
        }
        DemandeCompte d = nouvelle(TypeDemande.CREATION, null, demandeur);
        d.setLibelleCible(dto.getPrenom() + " " + dto.getNom() + " (" + dto.getMail() + ")");
        d.setDonnees(ecrire(dto));
        return notifierCheckers(demandeCompteRepo.save(d));
    }

    @Transactional
    public DemandeCompte soumettreModification(int id, UtilisateurRequestDTO dto, Utilisateur demandeur) {
        Utilisateur cible = cibleSansDemandeEnAttente(id);
        verifierSaisie(dto);
        Utilisateur memeMail = utilisateurService.getUtilisateurByMail(dto.getMail());
        if (memeMail != null && memeMail.getId() != id) {
            throw new IllegalArgumentException("Un utilisateur avec le mail '" + dto.getMail() + "' existe déjà.");
        }
        DemandeCompte d = nouvelle(TypeDemande.MODIFICATION, cible, demandeur);
        d.setDonnees(ecrire(dto));
        if (changements(d).isEmpty()) {
            throw new IllegalArgumentException("Aucune modification par rapport au compte actuel.");
        }
        return notifierCheckers(demandeCompteRepo.save(d));
    }

    @Transactional
    public DemandeCompte soumettreStatut(int id, boolean actif, Utilisateur demandeur) {
        Utilisateur cible = cibleSansDemandeEnAttente(id);
        if (cible.isActif() == actif) {
            throw new IllegalArgumentException("Le compte est déjà " + (actif ? "actif." : "inactif."));
        }
        if (!actif && cible.getId() == demandeur.getId()) {
            throw new IllegalArgumentException("Vous ne pouvez pas demander la désactivation de votre propre compte.");
        }
        return notifierCheckers(demandeCompteRepo.save(nouvelle(actif ? TypeDemande.ACTIVATION : TypeDemande.DESACTIVATION, cible, demandeur)));
    }

    // ── Décision (valideur) ──────────────────────────────────────────────────

    /**
     * Valide la demande et l'applique au compte, en une transaction : si l'application échoue
     * (email pris entre-temps, supérieur introuvable…), la demande reste en attente.
     */
    @Transactional
    public DemandeCompte valider(int demandeId, Utilisateur valideur, Authentication auth) {
        DemandeCompte d = demandeAttendue(demandeId);
        exigerDroitDeDecision(d, valideur, auth);

        switch (d.getType()) {
            case CREATION -> d.setCible(utilisateurService.insererUtilisateur(lireDonneesOuErreur(d)));
            case MODIFICATION -> utilisateurService.modifierUtilisateur(d.getCible().getId(), lireDonneesOuErreur(d));
            case ACTIVATION -> utilisateurService.changerStatut(d.getCible().getId(), true, valideur.getId());
            case DESACTIVATION -> utilisateurService.changerStatut(d.getCible().getId(), false, valideur.getId());
        }
        return notifierMaker(decider(d, Statut.VALIDEE, valideur, null));
    }

    @Transactional
    public DemandeCompte rejeter(int demandeId, Utilisateur valideur, Authentication auth, String motif) {
        DemandeCompte d = demandeAttendue(demandeId);
        exigerDroitDeDecision(d, valideur, auth);
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif du rejet est obligatoire.");
        }
        return notifierMaker(decider(d, Statut.REJETEE, valideur, motif.trim()));
    }

    /** Le créateur retire sa demande tant qu'elle attend. */
    @Transactional
    public DemandeCompte annuler(int demandeId, Utilisateur demandeur) {
        DemandeCompte d = demandeAttendue(demandeId);
        if (d.getDemandeur() == null || d.getDemandeur().getId() != demandeur.getId()) {
            throw new IllegalArgumentException("Seul l'auteur d'une demande peut l'annuler.");
        }
        return decider(d, Statut.ANNULEE, demandeur, null);
    }

    /** Valider ou rejeter : valideur IT, autre personne que l'auteur, et rôle ADMIN réservé aux administrateurs. */
    public boolean peutDecider(DemandeCompte d, Utilisateur valideur, Authentication auth) {
        return d.estEnAttente()
                && Habilitations.peutValiderDemandeDe(auth, Habilitations.IT, d.getDemandeur(), valideur)
                && (Habilitations.estAdmin(auth) || !toucheAuRoleAdmin(d));
    }

    // ── Affichage des changements ────────────────────────────────────────────

    /** Création : valeurs saisies ; modification : uniquement ce qui change ; (dés)activation : le statut. */
    public List<Changement> changements(DemandeCompte d) {
        List<Changement> liste = new ArrayList<>();
        switch (d.getType()) {
            case ACTIVATION -> liste.add(new Changement("Statut", "Inactif", "Actif"));
            case DESACTIVATION -> liste.add(new Changement("Statut", "Actif", "Inactif"));
            case CREATION, MODIFICATION -> {
                UtilisateurRequestDTO x = lireDonnees(d);
                if (x == null) break;
                Utilisateur c = d.getType() == TypeDemande.MODIFICATION ? d.getCible() : null;
                ajouter(liste, "Nom", c == null ? null : c.getNom(), x.getNom());
                ajouter(liste, "Prénom", c == null ? null : c.getPrenom(), x.getPrenom());
                ajouter(liste, "Email", c == null ? null : c.getMail(), x.getMail());
                ajouter(liste, "Contact", c == null ? null : c.getContact(), x.getContact());
                ajouter(liste, "Poste", c == null || c.getPoste() == null ? null : c.getPoste().getPoste(),
                        x.getPosteId() == null ? null : posteRepo.findById(x.getPosteId()).map(p -> p.getPoste()).orElse("#" + x.getPosteId()));
                ajouter(liste, "Service", c == null || c.getService() == null ? null : c.getService().getAcronyme(),
                        x.getServiceId() == null ? null : serviceRepo.findById(x.getServiceId()).map(s -> s.getAcronyme()).orElse("#" + x.getServiceId()));
                ajouter(liste, "Supérieur hiérarchique", c == null ? null : nomComplet(c.getSuperieurHierarchique()),
                        x.getSuperieurHierarchiqueId() == null ? null : nomComplet(utilisateurService.getUtilisateurById(x.getSuperieurHierarchiqueId())));
                ajouter(liste, "Rôles", c == null ? null : libellesRoles(c.getRoles()),
                        libellesRoles(new HashSet<>(roleRepo.findAllById(x.getRoleIds() == null ? Set.of() : x.getRoleIds()))));
                // Modification : null = validateurs inchangés
                if (c == null || x.getValidateurIds() != null) {
                    ajouter(liste, "Validateurs", c == null ? null : nomsUtilisateurs(c.getValidateurs()),
                            x.getValidateurIds() == null ? null : nomsUtilisateurs(x.getValidateurIds().stream()
                                    .map(utilisateurService::getUtilisateurById).filter(Objects::nonNull)
                                    .collect(Collectors.toSet())));
                }
            }
        }
        return liste;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Rôle ADMIN demandé, ou compte administrateur visé : seul un administrateur décide. */
    public boolean toucheAuRoleAdmin(DemandeCompte d) {
        if (Habilitations.estCompteAdmin(d.getCible())) return true;
        UtilisateurRequestDTO x = lireDonnees(d);
        return x != null && x.getRoleIds() != null && !x.getRoleIds().isEmpty()
                && roleRepo.findAllById(x.getRoleIds()).stream().anyMatch(Habilitations::estRoleAdmin);
    }

    // ── Notifications (envoyées après le commit : un échec d'envoi ne remet rien en cause) ──

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    /**
     * Prévient les checkers qu'une demande attend : IT_CHECKER, ou les seuls administrateurs si
     * elle touche au rôle ADMIN (eux seuls peuvent la traiter). Jamais l'auteur de la demande.
     */
    private DemandeCompte notifierCheckers(DemandeCompte d) {
        String role = toucheAuRoleAdmin(d) ? Habilitations.ADMIN : Habilitations.roleChecker(Habilitations.IT);
        Map<String, Utilisateur> destinataires = new LinkedHashMap<>();
        for (Utilisateur u : utilisateurService.getUtilisateursByRole(role)) {
            if (u.getMail() == null || u.getMail().isBlank()) continue;
            if (d.getDemandeur() != null && u.getId() == d.getDemandeur().getId()) continue;
            destinataires.putIfAbsent(u.getMail().trim().toLowerCase(), u);
        }
        if (destinataires.isEmpty()) {
            log.warn("Demande de compte {} : aucun destinataire ({}) à prévenir.", d.getId(), role);
            return d;
        }

        // Calculés maintenant, dans la transaction (le compte n'est pas encore modifié)
        List<Changement> changements = changements(d);
        String sujet = "[AFG Bank - Achat] - Demande de compte à valider : "
                + d.getType().getLibelle() + " — " + d.getLibelleCible();
        List<Mail> mails = new ArrayList<>();
        for (Utilisateur dest : destinataires.values()) {
            Map<String, Object> props = new HashMap<>();
            props.put("destinataire", dest);
            props.put("maker", d.getDemandeur());
            props.put("type", d.getType().getLibelle());
            props.put("compte", d.getLibelleCible());
            props.put("creation", d.getType() == TypeDemande.CREATION);
            props.put("changements", changements);
            props.put("dateDemande", d.getDateDemande().format(FORMAT_DATE));
            props.put("lienDemandes", emailSenderService.lien("/user/demandes"));
            mails.add(new Mail("compteDemandeSoumise", dest.getMail(), sujet, props));
        }
        apresCommit(() -> mails.forEach(emailSenderService::sendEmail));
        return d;
    }

    /** Prévient le maker de la décision (validation ou rejet, avec le motif). */
    private DemandeCompte notifierMaker(DemandeCompte d) {
        Utilisateur maker = d.getDemandeur();
        if (maker == null || maker.getMail() == null || maker.getMail().isBlank()) return d;
        boolean validee = d.getStatut() == Statut.VALIDEE;

        Map<String, Object> props = new HashMap<>();
        props.put("maker", maker);
        props.put("checker", d.getDecideur());
        props.put("validee", validee);
        props.put("type", d.getType().getLibelle());
        props.put("compte", d.getLibelleCible());
        props.put("motif", d.getMotifRejet());
        props.put("dateDemande", d.getDateDemande().format(FORMAT_DATE));
        props.put("dateDecision", d.getDateDecision().format(FORMAT_DATE));
        props.put("lienDemandes", emailSenderService.lien("/user/demandes"));
        Mail mail = new Mail("compteDemandeDecision", maker.getMail(),
                "[AFG Bank - Achat] - Demande de compte " + (validee ? "validée" : "rejetée")
                        + " : " + d.getType().getLibelle() + " — " + d.getLibelleCible(),
                props);
        apresCommit(() -> emailSenderService.sendEmail(mail));
        return d;
    }

    /** Exécute l'action après le commit de la transaction courante (ou tout de suite s'il n'y en a pas). */
    private static void apresCommit(Runnable action) {
        Runnable sansEchec = () -> {
            try {
                action.run();
            } catch (Exception e) {
                log.error("Échec de l'envoi des notifications de demande de compte", e);
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

    private void exigerDroitDeDecision(DemandeCompte d, Utilisateur valideur, Authentication auth) {
        if (!Habilitations.peutValider(auth, Habilitations.IT)) {
            throw new IllegalArgumentException("La validation des comptes est réservée au rôle "
                    + Habilitations.roleChecker(Habilitations.IT) + ".");
        }
        if (!Habilitations.peutValiderDemandeDe(auth, Habilitations.IT, d.getDemandeur(), valideur)) {
            throw new IllegalArgumentException("Vous ne pouvez pas valider votre propre demande : une autre personne doit le faire.");
        }
        if (!Habilitations.estAdmin(auth) && toucheAuRoleAdmin(d)) {
            throw new IllegalArgumentException("Cette demande concerne le rôle ADMIN : seul un administrateur peut la traiter.");
        }
    }

    private DemandeCompte nouvelle(TypeDemande type, Utilisateur cible, Utilisateur demandeur) {
        DemandeCompte d = new DemandeCompte();
        d.setType(type);
        d.setStatut(Statut.EN_ATTENTE);
        d.setCible(cible);
        if (cible != null) {
            d.setLibelleCible(cible.getPrenom() + " " + cible.getNom() + " (" + cible.getMail() + ")");
        }
        d.setDemandeur(demandeur);
        d.setDateDemande(LocalDateTime.now());
        return d;
    }

    private DemandeCompte decider(DemandeCompte d, Statut statut, Utilisateur auteur, String motif) {
        d.setStatut(statut);
        d.setDecideur(auteur);
        d.setDateDecision(LocalDateTime.now());
        d.setMotifRejet(motif);
        return demandeCompteRepo.save(d);
    }

    private DemandeCompte demandeAttendue(int id) {
        DemandeCompte d = demandeCompteRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));
        if (!d.estEnAttente()) {
            throw new IllegalArgumentException("Cette demande a déjà été traitée (" + d.getStatut().getLibelle().toLowerCase() + ").");
        }
        return d;
    }

    private Utilisateur cibleSansDemandeEnAttente(int id) {
        Utilisateur cible = utilisateurService.getUtilisateurById(id);
        if (cible == null) {
            throw new IllegalArgumentException("Utilisateur introuvable : id=" + id);
        }
        if (demandeCompteRepo.existsByCibleIdAndStatut(id, Statut.EN_ATTENTE)) {
            throw new IllegalArgumentException("Une demande attend déjà une validation pour ce compte : "
                    + "elle doit être validée, rejetée ou annulée avant une nouvelle.");
        }
        return cible;
    }

    private static void verifierSaisie(UtilisateurRequestDTO dto) {
        if (dto.getNom() == null || dto.getNom().isBlank()
                || dto.getPrenom() == null || dto.getPrenom().isBlank()
                || dto.getMail() == null || dto.getMail().isBlank()) {
            throw new IllegalArgumentException("Le nom, le prénom et l'email sont obligatoires.");
        }
    }

    private String ecrire(UtilisateurRequestDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Enregistrement de la demande impossible : " + e.getMessage(), e);
        }
    }

    public UtilisateurRequestDTO lireDonnees(DemandeCompte d) {
        if (d.getDonnees() == null || d.getDonnees().isBlank()) return null;
        try {
            return objectMapper.readValue(d.getDonnees(), UtilisateurRequestDTO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private UtilisateurRequestDTO lireDonneesOuErreur(DemandeCompte d) {
        UtilisateurRequestDTO x = lireDonnees(d);
        if (x == null) throw new IllegalArgumentException("Les données de la demande sont illisibles.");
        return x;
    }

    private static void ajouter(List<Changement> liste, String libelle, String avant, String apres) {
        String a = vide(avant), b = vide(apres);
        if (!Objects.equals(a, b)) liste.add(new Changement(libelle, a, b));
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nomComplet(Utilisateur u) {
        return u == null ? null : u.getPrenom() + " " + u.getNom();
    }

    private static String libellesRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) return null;
        return roles.stream().map(Role::getRole).sorted().collect(Collectors.joining(", "));
    }

    private static String nomsUtilisateurs(Set<Utilisateur> utilisateurs) {
        if (utilisateurs == null || utilisateurs.isEmpty()) return null;
        return utilisateurs.stream().map(DemandeCompteService::nomComplet).sorted().collect(Collectors.joining(", "));
    }
}
