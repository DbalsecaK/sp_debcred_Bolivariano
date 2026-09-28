package com.nexti.debcred;

/**
 * What block B12 needs, as the working values of the legacy variables: {@code servicio} after the
 * SPI-return lookup, {@code frmPagcobDeb} = {@code isnull(@i_frm_pagcob_deb, @i_frm_pagcob)} (line 262),
 * {@code actTotord} ({@code @w_act_totord}, 'N' for an SPI return), {@code codErrord} (0 on this path) and
 * {@code priorRowCount}, the value {@code @wRowdbBiz} holds when B12 starts (NULL until Phase 4).
 */
public record OrderHeaderContext(DebitRequest request, String servicio, String frmPagcobDeb, String actTotord,
                                 Integer codErrord, Integer priorRowCount) {
}
