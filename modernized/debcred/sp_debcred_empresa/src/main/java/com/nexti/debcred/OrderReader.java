package com.nexti.debcred;

import java.util.Optional;

/** {@code db_biz_pagos..bp_orden} then {@code db_sat_his..bp_orden_his} (lines 1352-1368). RULE-023, RULE-024. */
public interface OrderReader {

    Optional<String> liveService(Integer ordenBanco);

    Optional<String> historyService(Integer ordenBanco);
}
