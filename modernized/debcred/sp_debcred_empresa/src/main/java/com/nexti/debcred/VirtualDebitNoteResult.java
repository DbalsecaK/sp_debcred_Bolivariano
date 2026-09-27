package com.nexti.debcred;

/** {@code sp_vi_ndc_automatica}: the debit error code is the RETURN VALUE (line 666), never {@code error}; {@code ssnMonet} is the monetary sequence. */
public record VirtualDebitNoteResult(int returnCode, Integer error, Integer ssnMonet) {
}
