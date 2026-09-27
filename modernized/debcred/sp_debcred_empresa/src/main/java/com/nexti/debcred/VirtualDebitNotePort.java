package com.nexti.debcred;

/** {@code cob_virtuales..sp_vi_ndc_automatica} (lines 628-662): automatic debit note on a virtual account (12). RULE-009, RULE-033. */
public interface VirtualDebitNotePort {

    VirtualDebitNoteResult debit(VirtualDebitNoteCommand command);
}
