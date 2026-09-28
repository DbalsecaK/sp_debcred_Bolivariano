package com.nexti.debcred;

/** Return status, {@code @o_error} after the call (may be NULL, brief section 7 A15) and {@code @o_msg}. */
public record BasicNotificationResult(int returnCode, Integer oError, String oMsg) {
}
