package com.nexti.debcred;

/**
 * Block B7, notifications (lines 746-1256): Phase 4. Called only for account types 3, 4 and 12 when the
 * debit returned 0 and the service is not {@code PAGOPRV} (line 746). A configured outcome overwrites
 * the debit error code (line 1248) and may replace the working debit value (lines 884-886, RULE-003).
 */
public interface NotificationStep {

    NotificationOutcome notify(NotificationContext context);
}
