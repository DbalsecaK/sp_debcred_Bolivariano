package com.nexti.debcred;

import java.math.BigDecimal;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blocks B10 and B11 of {@code sp_debcred_empresa} (lines 1496-1856): the separate commission and the
 * second SWIFT commission, both through {@code sp_grb_comision} on the same ASE connection.
 *
 * <p>Preserved as-is (RULE-013 confirmed, brief section 7 A14):
 * <ul>
 *   <li><b>Exit B</b> (1606-1700, REF44): when the first commission fails, the <b>whole</b> transaction
 *       is rolled back, debit included, then the failed-commission movement is written outside any
 *       transaction (autocommit) and the procedure returns {@code @o_error} without commit. When the
 *       failure left {@code @o_error} at 0 the caller receives 0 / 0 (A14). The legacy checks neither the
 *       return value nor {@code @@error} of that movement write (1616), so a failure there is logged
 *       and ignored here too (architecture review M1, parity).</li>
 *   <li>The second SWIFT commission's failure goes to {@code lbl_error} instead (1824-1836): full
 *       rollback, no movement written here (RULE-017).</li>
 *   <li>Lines 1716-1730 and 1844-1854 are unreachable and are not implemented.</li>
 *   <li>TRANSQUICK with an SPI payment form swaps the working payment form for everything after
 *       (RULE-025); TRANSBIMO's tariff is its commission (RULE-001); the six REF33 fields pass through
 *       unchanged on the first call only (RULE-039).</li>
 * </ul>
 */
public final class CommissionDebits implements CommissionStep {

    private static final Logger log = LoggerFactory.getLogger(CommissionDebits.class);

    /** {@code @i_referencia} of the separate commission and of its failure movement (1556, 1652). */
    public static final String COMMISSION_REFERENCE = "COBRO DE COMISION";
    /** Generic commission failure (1602, 1802), used only on the SWIFT path (RULE-017). */
    public static final int COMMISSION_FAILED = 122003;
    /** {@code @i_tipoafec} of the second SWIFT commission (1786). RULE-016. */
    public static final String SWIFT_SECOND_COMMISSION_AFFECTATION = "16";

    private final CommissionPort commissions;
    private final MovementPort movements;
    private final AseTransaction tx;

    public CommissionDebits(CommissionPort commissions, MovementPort movements, AseTransaction tx) {
        this.commissions = commissions;
        this.movements = movements;
        this.tx = tx;
    }

    @Override
    public CommissionOutcome apply(CommissionContext ctx) {
        DebitRequest r = ctx.request();

        // 1498-1502 (RULE-025) and 1504-1508 (RULE-001): the working payment form and tariff
        String paymentForm = ctx.frmPagcob();
        if (AseText.equalsIgnoringTrailingBlanks(ctx.servicio(), "TRANSQUICK") && r.iFrmPagcobSpi() != null) {
            paymentForm = r.iFrmPagcobSpi();
        }
        BigDecimal tariff = r.iValorTarifa();
        if (AseText.equalsIgnoringTrailingBlanks(ctx.servicio(), "TRANSBIMO")) {
            tariff = ctx.valorComision();
        }

        // B10, 1512-1700: the separate commission (RULE-013, RULE-039)
        if (AseText.isPositive(ctx.valorComision())) {
            Charge charge = charge(separateCommission(ctx, paymentForm, tariff));
            if (charge.failed()) {
                return exitB(ctx, paymentForm, charge);
            }
        }

        // B11, 1742-1836: the second SWIFT commission (RULE-016, RULE-017); isnull(@i_valor2_swift, 0) > 0
        if (AseText.isPositive(r.iValor2Swift()) && AseText.trimmedEquals(ctx.servicio(), "TRANSWIFT")) {
            Charge charge = charge(swiftCommission(ctx, paymentForm));
            if (charge.failed()) {
                int numError = charge.oError() != 0 ? charge.oError() : COMMISSION_FAILED;
                log.warn("second SWIFT commission failed for order {} (company {}): return {}, o_error {} -> lbl_error {}",
                        r.iOrden(), r.iEmpresa(), charge.returnCode(), charge.oError(), numError);
                return new CommissionOutcome.LblError(numError);
            }
        }
        return new CommissionOutcome.Continue(paymentForm);
    }

    /**
     * Exit B (1610-1700, REF44): {@code rollback tran}, then the failed-commission movement in autocommit,
     * then return {@code @o_error}. {@code @w_num_error} is computed by the legacy and never used here.
     */
    private CommissionOutcome exitB(CommissionContext ctx, String paymentForm, Charge charge) {
        DebitRequest r = ctx.request();
        // Architecture review H3: the one path where a caller can see 0 / 0 with no money moved (A14).
        log.warn("exit B: commission failed for order {} (company {}, service {}): return {}, o_error {}{}; "
                        + "debit rolled back, caller receives {} / {}",
                r.iOrden(), r.iEmpresa(), ctx.servicio(), charge.returnCode(), charge.oError(),
                charge.cause() == null ? "" : ", " + describe(charge.cause()), charge.oError(), charge.oError());
        tx.rollback();
        try {
            movements.record(new MovementCommand(r.sUser(), ctx.terminal(), r.sOfi(), ctx.trn(), r.iTipoProceso(),
                    r.iEmpresa(), r.iProducto(), r.iOrden(), r.iCanal(), ctx.causal(), "X", charge.oError(), paymentForm,
                    r.iMonDebito(), ctx.valorComision(), "1", r.iFechaProceso(), COMMISSION_REFERENCE, 0,
                    ctx.servicio(), r.iTipoPagcob(), r.iPaisCta(), r.iCodBancoCta(), r.iTipctaEmp(), r.iNumctaEmp(),
                    r.iValorOrdenado(), r.iNemEmp(), r.iLocalidadOrden(), null, null, r.iOrdenEmpresa(),
                    ctx.comision(), ctx.tranNcnd()));
        } catch (AsePortException failure) {
            // 1616: neither @w_return nor @@error is checked after this write; parity (review M1).
            log.error("exit B: the failed-commission movement for order {} could not be recorded ({}); ignored as the legacy did",
                    r.iOrden(), describe(failure));
        }
        return new CommissionOutcome.ExitB(charge.oError());
    }

    /** The first call (1518-1594): literal reference, no {@code @i_tipoafec}, the six REF33 fields. */
    private static CommissionCommand separateCommission(CommissionContext ctx, String paymentForm, BigDecimal tariff) {
        DebitRequest r = ctx.request();
        return command(ctx, paymentForm, ctx.valorComision(), COMMISSION_REFERENCE, null, tariff,
                r.iValorComisionCue(), r.iValorTarifaEfe(), r.iValorComisionEfe(), r.iValorTarifaChe(),
                r.iValorComisionChe());
    }

    /** The SWIFT call (1746-1820): the request's reference, {@code @i_tipoafec '16'}, only {@code @i_valor_tarifa}. */
    private static CommissionCommand swiftCommission(CommissionContext ctx, String paymentForm) {
        DebitRequest r = ctx.request();
        return command(ctx, paymentForm, r.iValor2Swift(), r.iReferencia(), SWIFT_SECOND_COMMISSION_AFFECTATION,
                r.iValor2Swift(), null, null, null, null, null);
    }

    private static CommissionCommand command(CommissionContext ctx, String paymentForm, BigDecimal amount,
                                             String reference, String affectation, BigDecimal tariff,
                                             BigDecimal commissionCue, BigDecimal tariffEfe, BigDecimal commissionEfe,
                                             BigDecimal tariffChe, BigDecimal commissionChe) {
        DebitRequest r = ctx.request();
        return new CommissionCommand(ctx.sSsn(), r.sSrv(), r.sUser(), ctx.terminal(), r.sOfi(), r.iAplcobis(),
                r.iSpName(), r.iFechaProceso(), r.iCanal(), r.iEmpresa(), r.iProducto(), ctx.servicio(),
                r.iTipoProceso(), r.iOrden(), amount, ctx.cadena(), r.iTarjeta(), paymentForm, r.iMonDebito(),
                r.iTipctaEmp(), r.iNumctaEmp(), reference, r.iRefProv(), r.iTipoPagcob(), r.iPaisCta(),
                r.iCodBancoCta(), r.iNemEmp(), r.iLocalidadOrden(), null, null, r.iOrdenEmpresa(),
                r.iTipoReferencia(), affectation, ctx.savepoint(), 0, tariff, commissionCue, tariffEfe, commissionEfe,
                tariffChe, commissionChe);
    }

    /**
     * {@code if @w_return != 0 or @@error != 0 or @w_cod_errord != 0} (1606, 1824). A thrown
     * {@link AsePortException} is {@code @@error}; the output variable then keeps the 0 it held.
     */
    private Charge charge(CommissionCommand command) {
        try {
            CommissionResult result = commissions.charge(command);
            int oError = result.oError() == null ? 0 : result.oError();
            return new Charge(result.returnCode() != 0 || oError != 0, result.returnCode(), oError, null);
        } catch (AsePortException failure) {
            return new Charge(true, 0, 0, failure);
        }
    }

    private static String describe(AsePortException failure) {
        return failure.getCause() instanceof SQLException sql
                ? "@@error: SQLState " + sql.getSQLState() + ", code " + sql.getErrorCode()
                : "@@error: " + failure.getMessage();
    }

    private record Charge(boolean failed, int returnCode, int oError, AsePortException cause) {
    }
}
