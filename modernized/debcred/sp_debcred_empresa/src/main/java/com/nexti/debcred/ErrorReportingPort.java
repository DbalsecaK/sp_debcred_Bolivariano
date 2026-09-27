package com.nexti.debcred;

/** {@code cobis..sp_cerror} (lines 2152-2156): COBIS error reporting in {@code lbl_error} when {@code @i_aplcobis = 'S'}. RULE-028. */
public interface ErrorReportingPort {

    void report(ErrorReport report);
}
