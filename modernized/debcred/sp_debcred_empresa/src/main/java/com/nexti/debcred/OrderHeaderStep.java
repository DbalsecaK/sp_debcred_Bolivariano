package com.nexti.debcred;

/** Block B12, order-state update (lines 1862-2090): {@link OrderHeaderTransition}. */
public interface OrderHeaderStep {

    /** 0 to continue to the commit; otherwise the {@code @w_num_error} for {@code lbl_error} (122004). */
    int update(OrderHeaderContext context);
}
