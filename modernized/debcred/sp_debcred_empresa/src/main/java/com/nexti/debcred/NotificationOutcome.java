package com.nexti.debcred;

import java.math.BigDecimal;

/**
 * {@code configured == false}: no SMS mapping (line 814 false), nothing changes. Otherwise
 * {@code returnCode} becomes the debit error code (line 1248) and a non-null {@code valorDebito}
 * replaces the working debit value.
 */
public record NotificationOutcome(boolean configured, int returnCode, BigDecimal valorDebito) {

    public static NotificationOutcome notConfigured() {
        return new NotificationOutcome(false, 0, null);
    }
}
