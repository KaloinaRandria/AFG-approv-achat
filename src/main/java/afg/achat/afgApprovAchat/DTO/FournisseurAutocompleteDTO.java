package afg.achat.afgApprovAchat.DTO;

public record FournisseurAutocompleteDTO(
        int id,
        String nom,
        Integer responsableFournisseurId,
        Integer responsableId,
        String responsableNom,
        Integer adresseFournisseurId,   // ID de la liaison AdresseFournisseur
        Integer adresseId,              // ID de l'Adresse
        String adresseLibelle           // libellé de l'adresse
) {}