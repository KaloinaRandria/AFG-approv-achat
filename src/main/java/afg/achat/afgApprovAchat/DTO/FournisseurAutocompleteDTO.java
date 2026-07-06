package afg.achat.afgApprovAchat.DTO;

public record FournisseurAutocompleteDTO(
        int id,
        String nom,
        Integer responsableFournisseurId,  // ID de la liaison ResponsableFournisseur
        Integer responsableId,             // ID du Responsable (la personne)
        String responsableNom
) {}