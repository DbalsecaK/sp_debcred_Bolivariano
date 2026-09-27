package com.nexti.debcred;

/**
 * What the procedure hands back: its return value, {@code @o_error} and {@code @o_reg_a_proc}, which
 * is never assigned by the legacy and is always null (brief section 7, A11). RULE-028.
 */
public record DebitResult(int returnCode, Integer oError, Integer oRegAProc) {
}
