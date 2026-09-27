package com.nexti.debcred;

/** {@code db_biz_admempresa..sp_con_confcontable} (lines 322-374, 500-524): transaction code and causal. RULE-007, RULE-030, RULE-031. */
public interface AccountingConfigurationPort {

    AccountingConfiguration resolve(AccountingConfigurationQuery query);
}
