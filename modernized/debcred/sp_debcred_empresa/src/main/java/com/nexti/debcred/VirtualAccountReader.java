package com.nexti.debcred;

/** {@code cob_virtuales..vi_cuenta} (lines 300-304). RULE-029. */
public interface VirtualAccountReader {

    /** A row with {@code vi_cta_banco = ctaBanco} and {@code vi_prod_banc = 13} exists: a basic account. */
    boolean isBasicAccount(String ctaBanco);
}
