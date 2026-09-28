package com.nexti.debcred;

/** How the commission step ended, and therefore how the debit ends. */
public sealed interface CommissionOutcome {

    /** Both commissions done or not due; {@code frmPagcob} is the working payment form after the TRANSQUICK swap. */
    record Continue(String frmPagcob) implements CommissionOutcome {
    }

    /**
     * Exit B (1606-1700): the step already rolled the whole transaction back and wrote the failed-commission
     * movement in autocommit; the procedure returns {@code oError} in both channels, with no commit.
     */
    record ExitB(int oError) implements CommissionOutcome {
    }

    /** The second SWIFT commission failed (1824-1836): {@code goto lbl_error} with {@code numError}. */
    record LblError(int numError) implements CommissionOutcome {
    }
}
