package com.nexti.debcred;

import java.math.BigDecimal;

/**
 * What block B7 leaves behind. {@code configured == false}: no SMS mapping (line 814 false), nothing
 * changes. Otherwise {@code returnCode} becomes the debit error code (line 1248); when
 * {@code replacesValorDebito}, {@code valorDebito} (NULL included, brief section 7 A19-b) replaces the
 * working debit value (886, RULE-003); {@code oError} is the working {@code @o_error} (the basic
 * notifier's output, A15; 0 otherwise) and {@code wRowdbBiz} the live-read row count (840/912; NULL when
 * neither read ran, A17).
 */
public record NotificationOutcome(boolean configured, int returnCode, BigDecimal valorDebito, Integer oError,
                                  Integer wRowdbBiz, boolean replacesValorDebito) {

    /** A non-null {@code valorDebito} replaces the working debit value; null means "no replacement". */
    public NotificationOutcome(boolean configured, int returnCode, BigDecimal valorDebito, Integer oError,
                               Integer wRowdbBiz) {
        this(configured, returnCode, valorDebito, oError, wRowdbBiz, valorDebito != null);
    }

    public NotificationOutcome(boolean configured, int returnCode, BigDecimal valorDebito) {
        this(configured, returnCode, valorDebito, 0, null);
    }

    public static NotificationOutcome notConfigured() {
        return new NotificationOutcome(false, 0, null, 0, null);
    }
}
