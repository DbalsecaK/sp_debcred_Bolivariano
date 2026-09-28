package com.nexti.debcred;

import java.util.List;

/**
 * The order-header UPDATE of block B12, on the live table {@code bp_total_orden} (1886-1898) and on the
 * SAT history table {@code db_sat_his..bp_total_orden_his} (1908-1920):
 * {@code set te_estado_proceso = 'T', te_codigo_error = codError where te_orden_banco = ordenBanco and
 * te_frm_pagcob in (paymentForms) and te_servicio = servicio and isnull(te_estado_proceso, 'I') = 'I'}.
 * Returns {@code @@rowcount}; an ASE failure is {@link AsePortException}. RULE-014, RULE-018.
 */
public interface OrderHeaderRepository {

    int markLiveInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError);

    int markHistoryInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError);
}
