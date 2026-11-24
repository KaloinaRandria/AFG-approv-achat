package afg.achat.afgApprovAchat.DTO;

import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;

/** Ligne de la liste des fournisseurs renvoyée par la recherche instantanée. */
public record FournisseurDTO(
        int id,
        String nom,
        String acronyme,
        String mail,
        String contact,
        String description
) {
    public static FournisseurDTO from(Fournisseur f) {
        return new FournisseurDTO(f.getId(), f.getNom(), f.getAcronyme(), f.getMail(),
                f.getContact(), f.getDescription());
    }
}
