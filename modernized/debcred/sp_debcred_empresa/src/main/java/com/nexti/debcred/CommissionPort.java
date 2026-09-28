package com.nexti.debcred;

/** {@code cobis..sp_grb_comision} (lines 1518, 1746): a commission debited as its own movement. RULE-013, RULE-016. */
public interface CommissionPort {

    CommissionResult charge(CommissionCommand command);
}
