package com.nexti.debcred;

/** Return code (the debit error code, line 738) and {@code @o_transaccion} (the monetary sequence) of {@code sp_ndc_ahcc}. */
public record DebitNoteResult(int returnCode, Integer transaccion) {
}
