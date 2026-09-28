package com.nexti.debcred;

import java.util.List;

/**
 * The order detail B7 reads, live first and then the SAT history ({@code db_sat_his}). One element per row
 * the legacy SELECT returned, so {@code size()} is its {@code @@rowcount}. RULE-019, RULE-004, RULE-036.
 */
public interface OrderDetailReader {

    /** 826-838: {@code db_biz_pagos..bp_orden x bp_detalle} by order, detail sequence and ordering company. */
    List<SwiftCreditDetail> liveSwiftCreditDetails(Integer ordenBanco, Integer secuencial, Integer ordenante);

    /** 844-856: the same over {@code db_sat_his..bp_orden_his x bp_detalle_his}. */
    List<SwiftCreditDetail> historySwiftCreditDetails(Integer ordenBanco, Integer secuencial, Integer ordenante);

    /** 892-910: {@code db_biz_pagos..bp_detalle x ad_cuentas_bce} by order. */
    List<InterbankCreditDetail> liveInterbankCreditDetails(Integer ordenBanco);

    /** 916-932: the same over {@code db_sat_his..bp_detalle_his}. */
    List<InterbankCreditDetail> historyInterbankCreditDetails(Integer ordenBanco);

    /** 1024-1030: the unqualified {@code bp_detalle}, in the procedure's home database (brief section 7 A8). */
    List<BeneficiaryDetail> liveBeneficiaryDetails(Integer ordenBanco);

    /** 1036-1044: {@code db_sat_his..bp_detalle_his}. */
    List<BeneficiaryDetail> historyBeneficiaryDetails(Integer ordenBanco);
}
