package com.nexti.debcred;

/** {@code @o_trn}, {@code @o_cau} and the return code of {@code sp_con_confcontable}; nulls are what the procedure returned. */
public record AccountingConfiguration(int returnCode, Integer trn, String causal) {
}
