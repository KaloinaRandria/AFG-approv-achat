package afg.achat.afgApprovAchat.DTO;

public record FournisseurAutocompleteDTO(
        int id,
        String nom,
        Integer responsableId,
        String responsableNom
) {}