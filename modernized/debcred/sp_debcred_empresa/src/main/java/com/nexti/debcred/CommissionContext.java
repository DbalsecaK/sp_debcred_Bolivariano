package com.nexti.debcred;

import java.math.BigDecimal;

/**
 * The <b>working</b> values of the legacy variables blocks B10/B11 read, not the request's:
 * {@code comision}/{@code valorComision} after normalization (188-196), {@code trn}/{@code causal} as the
 * debit used them (after a CORPEI re-resolution), {@code tranNcnd} ({@code @w_tran_ncnd}, null for an
 * accounting account), {@code cadena} ({@code @w_cadena}, null for an accounting account), {@code servicio}
 * ({@code @i_servicio} after the SPI-return lookup), {@code terminal} ({@code @s_term}, blank for SPI) and
 * {@code sSsn} ({@code @s_ssn ?? 0}). {@code savepoint} is passed to {@code sp_grb_comision} (1578).
 */
public record CommissionContext(DebitRequest request, BigDecimal comision, BigDecimal valorComision, String frmPagcob,
                                Integer trn, String causal, String savepoint, Integer tranNcnd, String cadena,
                                String servicio, String terminal, Integer sSsn) {
}
