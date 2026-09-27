package com.nexti.debcred;

import java.math.BigDecimal;

/** What B10/B11 need from the debit; {@code savepoint} is passed to {@code sp_grb_comision} (line 1584). */
public record CommissionContext(DebitRequest request, BigDecimal comision, BigDecimal valorComision,
                                String frmPagcob, Integer trn, String causal, String savepoint) {
}
