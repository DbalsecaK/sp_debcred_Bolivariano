package com.nexti.debcred;

/** Blocks B10 and B11, commissions (lines 1496-1856): {@link CommissionDebits}. */
public interface CommissionStep {

    CommissionOutcome apply(CommissionContext context);
}
