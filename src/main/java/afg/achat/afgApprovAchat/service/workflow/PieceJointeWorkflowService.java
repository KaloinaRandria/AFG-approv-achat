package afg.achat.afgApprovAchat.service.workflow;

import afg.achat.afgApprovAchat.model.demande.CodepPieceJointe;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandePieceJointe;
import afg.achat.afgApprovAchat.service.demande.CodepPieceJointeService;
import afg.achat.afgApprovAchat.service.demande.DemandePieceJointeService;
import afg.achat.afgApprovAchat.upload.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Contrôle et enregistrement des pièces jointes des demandes et du CODEP. */
@Service
@RequiredArgsConstructor
public class PieceJointeWorkflowService {

    /** Formats acceptés (identiques à l'attribut accept des formulaires). */
    public static final Set<String> MIME_AUTORISES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final StorageService storageService;
    private final DemandePieceJointeService demandePieceJointeService;
    private final CodepPieceJointeService codepPieceJointeService;

    public static boolean formatAutorise(MultipartFile f) {
        return f.getContentType() != null && MIME_AUTORISES.contains(f.getContentType());
    }

    /** @throws WorkflowException si un fichier n'a pas un format autorisé. */
    public void verifierFormats(MultipartFile[] fichiers) {
        if (fichiers == null) return;
        for (MultipartFile f : fichiers) {
            if (f == null || f.isEmpty()) continue;
            if (!formatAutorise(f)) {
                throw new WorkflowException("Fichier refusé : '" + f.getOriginalFilename()
                        + "'. Seuls les images (JPEG, PNG, GIF, WebP) et PDF sont autorisés.");
            }
        }
    }

    /** Enregistre des pièces jointes de demande et renvoie leurs noms d'origine. */
    public List<String> enregistrerPourDemande(MultipartFile[] fichiers, DemandeMere demande, String prefixe) {
        verifierFormats(fichiers);
        List<String> noms = new ArrayList<>();
        if (fichiers == null) return noms;
        for (MultipartFile f : fichiers) {
            if (f == null || f.isEmpty()) continue;
            enregistrerUnePourDemande(f, demande, prefixe);
            noms.add(f.getOriginalFilename());
        }
        return noms;
    }

    public DemandePieceJointe enregistrerUnePourDemande(MultipartFile f, DemandeMere demande, String prefixe) {
        DemandePieceJointe pj = new DemandePieceJointe();
        pj.setDemande(demande);
        pj.setOriginalName(f.getOriginalFilename());
        pj.setStoredName(stocker(f, demande, prefixe));
        pj.setContentType(f.getContentType());
        pj.setSize(f.getSize());
        pj.setUploadedAt(LocalDateTime.now());
        return demandePieceJointeService.insert(pj);
    }

    /** Enregistre des pièces jointes CODEP et renvoie leurs noms d'origine. */
    public List<String> enregistrerPourCodep(MultipartFile[] fichiers, DemandeMere demande, String prefixe) {
        verifierFormats(fichiers);
        List<String> noms = new ArrayList<>();
        if (fichiers == null) return noms;
        for (MultipartFile f : fichiers) {
            if (f == null || f.isEmpty()) continue;

            CodepPieceJointe pj = new CodepPieceJointe();
            pj.setDemandeMere(demande);
            pj.setOriginalName(f.getOriginalFilename());
            pj.setStoredName(stocker(f, demande, prefixe));
            pj.setContentType(f.getContentType());
            pj.setSize(f.getSize());
            pj.setUploadedAt(LocalDateTime.now());

            codepPieceJointeService.insert(pj);
            noms.add(f.getOriginalFilename());
        }
        return noms;
    }

    /** Vrai si le fichier stocké appartient à la demande (PJ de demande ou CODEP). */
    public boolean appartientALaDemande(String demandeId, String storedName) {
        return demandePieceJointeService.listByDemandeId(demandeId).stream()
                       .anyMatch(pj -> storedName.equals(pj.getStoredName()))
                || codepPieceJointeService.listByDemandeId(demandeId).stream()
                       .anyMatch(pj -> storedName.equals(pj.getStoredName()));
    }

    private String stocker(MultipartFile f, DemandeMere demande, String prefixe) {
        if (!formatAutorise(f)) {
            throw new WorkflowException("Fichier refusé : " + f.getOriginalFilename());
        }
        String ref = (prefixe == null || prefixe.isEmpty() ? "" : prefixe + "_")
                + demande.getId()
                + "_" + demande.getDemandeur().getNom()
                + "_" + demande.getDemandeur().getPrenom()
                + "_" + LocalDateTime.now().format(HORODATAGE);
        return storageService.store(f, ref);
    }
}
