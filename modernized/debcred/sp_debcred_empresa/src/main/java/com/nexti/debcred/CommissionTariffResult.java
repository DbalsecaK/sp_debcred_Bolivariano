package com.nexti.debcred;

import java.math.BigDecimal;

/** {@code @o_valor_por_tran} and the return code of {@code sp_con_comision}. */
public record CommissionTariffResult(int returnCode, BigDecimal valorPorTran) {
}
