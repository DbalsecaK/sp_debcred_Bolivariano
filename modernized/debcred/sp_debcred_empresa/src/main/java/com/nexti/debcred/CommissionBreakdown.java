package com.nexti.debcred;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Block B1, commission normalization and the per-transaction tariff (sp_debcred_empresa.sp:184-248).
 *
 * <p>RULE-027: a commission bundled with the debit ({@code @i_comision > 0}) and no separate
 * commission ({@code @i_valor_comision = 0}, exactly zero, not NULL) is moved to the separate
 * commission, so commissions are always posted as their own movement (ref28).
 *
 * <p>RULE-002 (confirmed as-is, 2026-09-27): the transaction count is the commission divided by the
 * configured per-transaction tariff, as a money division (4 decimals, HALF_UP) truncated toward zero
 * to an {@code int}; the unit commission handed to {@code sp_ndc_ahcc} is the configured tariff
 * itself, unconditionally, even when the tariff lookup failed and left it at 0 (line 248).
 *
 * @param comision       {@code @i_comision} after normalization
 * @param valorComision  {@code @i_valor_comision} after normalization
 * @param cantidad       {@code @w_cantidad}, passed as {@code @i_nchq}
 * @param comisionUnd    {@code @w_comision_und}, passed as {@code @i_solca}
 */
record CommissionBreakdown(BigDecimal comision, BigDecimal valorComision, int cantidad, BigDecimal comisionUnd) {

    private static final int MONEY_SCALE = 4;

    static CommissionBreakdown of(DebitRequest request, CommissionTariffPort tariffs) {
        BigDecimal comision = request.iComision();
        BigDecimal valorComision = request.iValorComision();
        if (AseText.isPositive(comision) && valorComision != null && valorComision.signum() == 0) {   // 188-196
            valorComision = comision;
            comision = BigDecimal.ZERO;
        }

        CommissionTariffResult tariff = tariffs.consult(new CommissionTariffQuery(                    // 204-222
                request.iEmpresa(), request.iProducto(), request.iServicio(), request.iCanal(),
                "E", "01", 1, request.iAplcobis()));
        BigDecimal valPorTran = tariff.returnCode() != 0                                             // 224-226
                ? BigDecimal.ZERO
                : AseText.zeroIfNull(tariff.valorPorTran());

        BigDecimal comisionUnd = BigDecimal.ZERO;                                                    // 230-238
        if (AseText.isPositive(comision)) {
            comisionUnd = comision;
        }
        if (AseText.isPositive(valorComision)) {
            comisionUnd = valorComision;
        }
        int cantidad = 0;
        if (valPorTran.signum() > 0) {                                                               // 242-244
            BigDecimal whole = comisionUnd.divide(valPorTran, MONEY_SCALE, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.DOWN);
            try {
                cantidad = whole.intValueExact();
            } catch (ArithmeticException overflow) {
                // The legacy int assignment would raise an arithmetic overflow (@@error <> 0); it is not tolerated.
                throw new AsePortException("commission count overflows int: " + whole, overflow);
            }
        }
        return new CommissionBreakdown(comision, valorComision, cantidad, valPorTran);                // 248
    }
}
