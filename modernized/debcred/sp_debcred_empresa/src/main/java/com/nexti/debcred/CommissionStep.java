package com.nexti.debcred;

/** Blocks B10 and B11, commissions (lines 1500-1856): Phase 2. Returns 0 to continue. */
public interface CommissionStep {

    int apply(CommissionContext context);
}
