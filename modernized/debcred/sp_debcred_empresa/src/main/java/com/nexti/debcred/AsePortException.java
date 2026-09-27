package com.nexti.debcred;

/**
 * A called procedure or statement failed at the ASE level: the equivalent of {@code @@error <> 0}.
 * The service maps it only where the legacy tests {@code @@error} (lines 382 and 1464); elsewhere
 * it propagates.
 */
public class AsePortException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AsePortException(String message) {
        super(message);
    }

    public AsePortException(String message, Throwable cause) {
        super(message, cause);
    }
}
