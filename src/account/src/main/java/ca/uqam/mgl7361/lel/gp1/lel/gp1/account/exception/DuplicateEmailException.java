package ca.uqam.mgl7361.lel.gp1.lel.gp1.account.exception;

public class DuplicateEmailException extends Exception {

    public DuplicateEmailException(String message) {
        super(message);
    }

    public DuplicateEmailException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
