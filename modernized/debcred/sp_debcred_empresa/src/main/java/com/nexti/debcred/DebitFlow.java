package com.nexti.debcred;

/**
 * Builds the debit over one ASE session: the steps that need the session's transaction and ports
 * (commissions since Phase 2; the order header and notifications in Phases 3 and 4) are created here,
 * per session, so the controller stays a thin edge (architecture review M2).
 */
@FunctionalInterface
public interface DebitFlow {

    DebitCompanyAccountService over(AseSession ase);
}
