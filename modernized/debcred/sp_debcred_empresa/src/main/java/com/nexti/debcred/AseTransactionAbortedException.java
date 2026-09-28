package com.nexti.debcred;

/**
 * ASE ended the transaction on its own: a deadlock victim (error 1205), a lost connection, or a
 * {@code commit tran} found {@code @@trancount = 0}. Unlike a statement-level {@code @@error}, which the
 * legacy leaves unchecked in several places, this aborts the batch in ASE and the caller sees the error;
 * it is therefore never swallowed (architecture review Phase 3 H1). The debit was not persisted.
 */
public class AseTransactionAbortedException extends AsePortException {

    private static final long serialVersionUID = 1L;

    public AseTransactionAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
