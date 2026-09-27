package com.nexti.debcred;

/** What B12 needs: the (possibly SPI-replaced) service, the debit payment form, {@code @w_act_totord} and the error code. */
public record OrderHeaderContext(DebitRequest request, String servicio, String frmPagcobDeb, String actTotord,
                                 Integer codErrord) {
}
