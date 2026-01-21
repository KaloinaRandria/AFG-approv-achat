package afg.achat.afgApprovAchat.exception;

public class FournisseurAlreadyExistsException extends RuntimeException {

    public FournisseurAlreadyExistsException(String nom) {
        super("Un fournisseur avec le nom \"" + nom + "\" existe déjà.");
    }
}

