package com.nexti.debcred;

import java.util.Optional;

/** The debtor's client in the account masters (1102-1154). RULE-005. */
public interface AccountOwnerReader {

    /** {@code cob_cuentas..cc_ctacte.cc_cliente}; empty for no row or a NULL client. */
    Optional<Integer> currentAccountClient(String ctaBanco);

    /** {@code cob_ahorros..ah_cuenta.ah_cliente}; empty for no row or a NULL client. */
    Optional<Integer> savingsAccountClient(String ctaBanco);

    /** {@code cob_virtuales..vi_cuenta}; present whenever the row exists, whatever its values. */
    Optional<VirtualAccountOwner> virtualAccountOwner(String ctaBanco);
}
