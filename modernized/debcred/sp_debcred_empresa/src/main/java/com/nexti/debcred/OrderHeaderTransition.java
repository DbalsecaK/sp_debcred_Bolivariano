package com.nexti.debcred;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block B12 of {@code sp_debcred_empresa} (lines 1862-2090): the order header moves from Initial to
 * In-Transition, live table first, then the SAT history table (RULE-014, RULE-018), and "no header row
 * moved" is error 122004 unless the service is exempt or the order is an SPI return (RULE-010).
 *
 * <p>Preserved as-is:
 * <ul>
 *   <li>A direct channel with an option outside {@code 01}-{@code 03} runs no UPDATE; the row count is
 *       then the stale {@code @wRowdbBiz} ({@link OrderHeaderContext#priorRowCount()}), which is NULL
 *       until Phase 4 and never raises 122004.</li>
 *   <li>Option {@code 01} matches the debit payment form only ({@code isnull(@i_frm_pagcob_deb,
 *       @i_frm_pagcob)}); a NULL form matches nothing but the UPDATEs still run.</li>
 *   <li>The legacy never checks {@code @@error} on these UPDATEs: a failure counts as 0 rows and the flow
 *       continues (approved at the Phase 3 plan gate).</li>
 * </ul>
 */
public final class OrderHeaderTransition implements OrderHeaderStep {

    private static final Logger log = LoggerFactory.getLogger(OrderHeaderTransition.class);

    /** No header row moved to 'T' (line 2084). RULE-010. */
    public static final int NO_HEADER_UPDATED = 122004;

    private static final List<String> DIRECT_CHANNELS = List.of("DIR", "SFR", "FR2", "BTH", "VEN");
    private static final List<String> OPTION_02_FORMS = List.of("CUE", "EFE", "CHL");
    private static final List<String> OPTION_03_FORMS = List.of("CUE", "EFE", "CHE");
    private static final List<String> OTHER_CHANNEL_FORMS = List.of("COB", "TRC", "CTB", "CPD", "TPD");
    private static final List<String> EXEMPT_SERVICES =
            List.of("TRANSWIFT", "IMPADUAN", "PAGIESS", "TRANSQUICK", "TRANSBIMO", "PAGOPRV");

    private final OrderHeaderRepository headers;

    public OrderHeaderTransition(OrderHeaderRepository headers) {
        this.headers = headers;
    }

    @Override
    public int update(OrderHeaderContext ctx) {
        DebitRequest r = ctx.request();
        List<String> forms = paymentForms(r.iCanal(), r.iOpcion(), ctx.frmPagcobDeb());

        Integer rows = ctx.priorRowCount();                                       // stale @wRowdbBiz
        if (forms != null) {
            int moved = mark(true, r.iOrden(), forms, ctx);                       // 1886-1902
            if (moved <= 0) {
                moved = mark(false, r.iOrden(), forms, ctx);                      // 1904-1922
            }
            rows = moved;
        }

        // 2076-2090: `if @wRowdbBiz = 0` (NULL is not 0); NULL service makes `not in` unknown, so false
        if (rows != null && rows == 0 && ctx.servicio() != null && !isExempt(ctx.servicio())
                && AseText.equalsIgnoringTrailingBlanks(ctx.actTotord(), "S")) {
            log.warn("no order header moved to 'T' for order {} (service {}): 122004", r.iOrden(), ctx.servicio());
            return NO_HEADER_UPDATED;
        }
        return 0;
    }

    /**
     * The payment forms the UPDATE targets (1876-2040), or null when no UPDATE runs: a direct channel
     * whose option is not {@code 01}, {@code 02} or {@code 03}.
     */
    private static List<String> paymentForms(String channel, String option, String debitPaymentForm) {
        boolean direct = DIRECT_CHANNELS.stream().anyMatch(c -> AseText.equalsIgnoringTrailingBlanks(channel, c));
        if (!direct) {
            return OTHER_CHANNEL_FORMS;
        }
        if (AseText.equalsIgnoringTrailingBlanks(option, "01")) {
            return Collections.singletonList(debitPaymentForm);
        }
        if (AseText.equalsIgnoringTrailingBlanks(option, "02")) {
            return OPTION_02_FORMS;
        }
        if (AseText.equalsIgnoringTrailingBlanks(option, "03")) {
            return OPTION_03_FORMS;
        }
        return null;
    }

    /**
     * One UPDATE. A statement-level {@code @@error} is not checked by the legacy, so it counts as 0 rows
     * (plan-gate decision). A lost transaction ({@link AseTransactionAbortedException}: deadlock victim,
     * lost connection) is never swallowed: ASE aborts the batch there too (review Phase 3 H1).
     */
    private int mark(boolean live, Integer order, List<String> forms, OrderHeaderContext ctx) {
        try {
            return live
                    ? headers.markLiveInTransition(order, forms, ctx.servicio(), ctx.codErrord())
                    : headers.markHistoryInTransition(order, forms, ctx.servicio(), ctx.codErrord());
        } catch (AseTransactionAbortedException lost) {
            throw lost;
        } catch (AsePortException failure) {
            log.error("order header update on the {} table failed for order {} ({}); counted as 0 rows as the legacy does",
                    live ? "live" : "history", order, describe(failure));
            return 0;
        }
    }

    private static String describe(AsePortException failure) {
        return failure.getCause() instanceof SQLException sql
                ? "SQLState " + sql.getSQLState() + ", code " + sql.getErrorCode()
                : failure.getMessage();
    }

    /** {@code @i_servicio not in (...)} with Sybase char semantics: trailing blanks ignored. */
    private static boolean isExempt(String service) {
        return EXEMPT_SERVICES.stream().anyMatch(s -> AseText.equalsIgnoringTrailingBlanks(service, s));
    }
}
