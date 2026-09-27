package com.nexti.debcred.ase;

import com.nexti.debcred.AsePortException;

/** No ASE connection could be opened: the debit never started. Reported to callers as 503. */
public class AseUnavailableException extends AsePortException {

    private static final long serialVersionUID = 1L;

    public AseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
