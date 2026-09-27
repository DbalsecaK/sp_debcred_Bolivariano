package com.nexti.debcred;

import java.math.BigDecimal;

/** What B7 reads from the debit that just happened. */
public record NotificationContext(DebitRequest request, String servicio, Integer trn, String causal,
                                  BigDecimal valorDebito, BigDecimal comision, BigDecimal valorComision,
                                  Integer tranNcnd) {
}
