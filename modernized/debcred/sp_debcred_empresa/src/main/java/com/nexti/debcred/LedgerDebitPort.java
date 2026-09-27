package com.nexti.debcred;

/** {@code db_biz_pagos..sp_graba_tran_servicio} (lines 1284-1298): debit on an accounting account (9). RULE-022. */
public interface LedgerDebitPort {

    LedgerDebitResult debit(LedgerDebitCommand command);
}
