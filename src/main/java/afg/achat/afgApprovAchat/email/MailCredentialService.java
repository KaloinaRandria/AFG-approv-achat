package afg.achat.afgApprovAchat.email;

import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.email.MailPasswordHistoryRepo;
import jakarta.annotation.PostConstruct;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MailCredentialService {

    private final MailPasswordHistoryRepo repository;
    private final PasswordCryptoUtil cryptoUtil;
    private final JavaMailSenderImpl mailSender;

    private static final String USERNAME_DEFAUT = "mada_afgbank.alerts@afgbank.mg";

    public MailCredentialService(MailPasswordHistoryRepo repository,
                                 PasswordCryptoUtil cryptoUtil,
                                 JavaMailSenderImpl mailSender) {
        this.repository = repository;
        this.cryptoUtil = cryptoUtil;
        this.mailSender = mailSender;
    }

    // Au démarrage de l'appli : on charge le mot de passe actif et on configure le mailSender
    @PostConstruct
    public void initMailSender() {
        repository.findByActifTrue().ifPresentOrElse(
                actif -> {
                    mailSender.setUsername(actif.getUsername());
                    mailSender.setPassword(cryptoUtil.decrypt(actif.getPasswordChiffre()));
                },
                () -> {
                    // Première mise en route : aucun mot de passe en base pour l'instant.
                    // L'IT devra le saisir via l'interface au premier lancement.
                    mailSender.setUsername(USERNAME_DEFAUT);
                    System.out.println("Aucun mot de passe mail configuré en base. " +
                            "Rendez-vous sur l'interface admin pour le saisir.");
                }
        );
    }

    // Appelé par le controller quand l'IT saisit un nouveau mot de passe
    public void changerMotDePasse(String username, String nouveauMotDePasse, Utilisateur admin) {

        // 1. Désactive l'ancien
        repository.findByActifTrue().ifPresent(ancien -> {
            ancien.setActif(false);
            repository.save(ancien);
        });

        // 2. Crée la nouvelle entrée d'historique
        MailPasswordHistory nouveau = new MailPasswordHistory();
        nouveau.setUsername(username);
        nouveau.setPasswordChiffre(cryptoUtil.encrypt(nouveauMotDePasse));
        nouveau.setDateModification(LocalDateTime.now());
        nouveau.setModifiePar(admin);
        nouveau.setActif(true);
        repository.save(nouveau);

        // 3. Reconfigure le mailSender EN DIRECT — pas besoin de redémarrer le serveur
        mailSender.setUsername(username);
        mailSender.setPassword(nouveauMotDePasse);

        System.out.println("Mot de passe mail mis à jour par " + admin.getNom() +
                " le " + nouveau.getDateModification());
    }

    public List<MailPasswordHistory> getHistorique() {
        return repository.findAllByOrderByDateModificationDesc();
    }

    // Renvoie true si le mot de passe actif date de plus de X jours (alerte proactive)
    public boolean motDePasseExpireBientot(int joursAvantExpiration) {
        return repository.findByActifTrue()
                .map(actif -> actif.getDateModification()
                        .isBefore(LocalDateTime.now().minusDays(joursAvantExpiration)))
                .orElse(true);
    }

    public LocalDateTime getDateDerniereModification() {
        return repository.findByActifTrue()
                .map(MailPasswordHistory::getDateModification)
                .orElse(null);
    }
}