package com.nexti.debcred;

/** {@code cobis..sp_ndc_ahcc} (lines 676-730): debit note on a current (3) or savings (4) account. RULE-009. */
public interface DebitNotePort {

    DebitNoteResult debit(DebitNoteCommand command);
}
