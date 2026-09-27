package com.nexti.debcred;

/** Block B12, order-state update (lines 1876-2090): Phase 3. Returns 0 to continue. */
public interface OrderHeaderStep {

    int update(OrderHeaderContext context);
}
