package com.nexti.debcred;

/** {@code db_biz_admempresa..sp_con_comision} (lines 204-222): the per-transaction commission tariff. RULE-002. */
public interface CommissionTariffPort {

    CommissionTariffResult consult(CommissionTariffQuery query);
}
