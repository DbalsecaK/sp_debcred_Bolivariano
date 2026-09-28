package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.HeaderTable;
import com.nexti.debcred.support.FakeAseSession.HeaderUpdate;
import com.nexti.debcred.support.Requests;

/**
 * RULE-010 (sp_debcred_empresa.sp:2076-2090):
 * <pre>
 * if @wRowdbBiz = 0
 *    if @i_servicio not in ('TRANSWIFT','IMPADUAN','PAGIESS','TRANSQUICK','TRANSBIMO','PAGOPRV') and @w_act_totord = 'S'
 *       select @w_num_error = 122004  goto lbl_error
 * </pre>
 * Pinned as-is: exit C with full rollback (aplcobis 'S': sp_cerror, {@code (122004, 0)}; else
 * {@code (0, 122004)}); SPI returns ({@code @w_act_totord = 'N'}) and a NULL service ({@code NULL not in}
 * is unknown) never raise it; a direct channel with an option outside 01/02/03 runs no UPDATE and keeps
 * the STALE {@code @wRowdbBiz} ({@code OrderHeaderContext.priorRowCount}, NULL today); and, by the
 * approved parity decision, an {@code @@error} on either UPDATE is not checked and counts as 0 rows.
 * Fake data only.
 */
class Rule010NoHeaderUpdatedTest {

    private static final int ORDER = Requests.BANK_ORDER;

    private static DebitRequest directOption(String option, String service) {
        return Requests.currentAccount().servicio(service).canal("DIR").opcion(option).build();
    }

    // ---- 122004 and its exit ------------------------------------------------------------------

    @Test
    @DisplayName("RULE-010 + RULE-028: nothing updated, not COBIS -> (0, 122004), full rollback, no commit, no sp_cerror, nothing committed")
    void rule010_rule028_noHeaderRowRaises122004NotCobis() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(Requests.currentAccount().aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.commits()).isZero();
        assertThat(ase.committedWrites()).as("debit and 'P' movement discarded").isEmpty();
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.transactionOpen()).isFalse();
        assertThat(ase.calls()).endsWith("sp_grb_mov_y_frmpgo", "commissionStep", "orderHeaderStep", "rollback");
    }

    @Test
    @DisplayName("RULE-010 + RULE-028: nothing updated, COBIS -> sp_cerror(sp_name, 122004), (122004, 0), full rollback")
    void rule010_rule028_noHeaderRowRaises122004Cobis() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(Requests.currentAccount().aplcobis("S").spName("sp_caller_fake").build());

        assertThat(result).isEqualTo(new DebitResult(122004, 0, null));
        assertThat(ase.onlyErrorReport()).isEqualTo(new ErrorReport("sp_caller_fake", 122004));
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.commits()).isZero();
        assertThat(ase.committedWrites()).isEmpty();
        assertThat(ase.calls()).endsWith("orderHeaderStep", "rollback", "sp_cerror");
    }

    @Test
    @DisplayName("RULE-010 + RULE-013: 122004 also discards the commission charged before it")
    void rule010_rule013_122004DiscardsTheCommission() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(Requests.currentAccount().comision("0").valorComision("2.00").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.onlyCommissionCommand()).isNotNull();
        assertThat(ase.committedWrites()).isEmpty();
    }

    @Test
    @DisplayName("RULE-010: the step itself returns the number 122004 for lbl_error")
    void rule010_stepReturns122004() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        int numError = new OrderHeaderTransition(ase).update(
                new OrderHeaderContext(directOption("02", "ROLPAGO"), "ROLPAGO", "CUE", "S", 0, null));

        assertThat(numError).isEqualTo(122004);
    }

    @ParameterizedTest(name = "RULE-010: exempt service ''{0}'' with no header row commits (0, 0)")
    @ValueSource(strings = {"TRANSWIFT", "IMPADUAN", "PAGIESS", "TRANSQUICK", "TRANSBIMO", "PAGOPRV", "TRANSWIFT ", "PAGOPRV  "})
    void rule010_exemptServicesNeverRaise122004(String service) {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio(service).build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::table).containsExactly(HeaderTable.LIVE, HeaderTable.HISTORY);
    }

    @ParameterizedTest(name = "RULE-010: non-exempt service ''{0}'' with no header row raises 122004")
    @ValueSource(strings = {"ROLPAGO", "TRANSCLI", "TARJCRED", "SPI", " TRANSWIFT", "TRANSWIFTX", "PAGIES"})
    void rule010_nonExemptServicesRaise122004(String service) {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio(service).tipoAfec("10").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
    }

    // ---- SPI return: act_totord 'N' ----------------------------------------------------------

    @Test
    @DisplayName("RULE-010 + RULE-023: SPI return, original service TRANSCLI, no header row -> updates tried under TRANSCLI, no 122004, commit")
    void rule010_rule023_spiReturnNeverRaises122004() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows().liveOrder(ORDER, "TRANSCLI");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().actTotord()).isEqualTo("N");
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::servicio).containsExactly("TRANSCLI", "TRANSCLI");
        assertThat(ase.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-010 + RULE-023: SPI return still moves the original order's header rows to 'T'")
    void rule010_rule023_spiReturnUpdatesTheOriginalServiceHeader() {
        FakeAseSession ase = new FakeAseSession().liveOrder(ORDER, "TRANSCLI")
                .headerRow(ORDER, "CUE", "TRANSCLI", "I")
                .headerRow(ORDER, "CUE", "SPI", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerState(ORDER, "CUE", "TRANSCLI")).isEqualTo("T");
        assertThat(ase.headerState(ORDER, "CUE", "SPI")).isEqualTo("I");
    }

    @Test
    @DisplayName("RULE-010 + RULE-024: SPI return for an order found nowhere -> service NULL, nothing matches, no 122004")
    void rule010_rule024_spiReturnUnknownOrderCommits() {
        FakeAseSession ase = new FakeAseSession().headerRow(ORDER, "CUE", "SPI", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerState(ORDER, "CUE", "SPI")).isEqualTo("I");
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::servicio).containsExactly(null, null);
    }

    @Test
    @DisplayName("RULE-010: act_totord 'N' in the context suppresses 122004 for a non-exempt service")
    void rule010_actTotordNSuppresses122004() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        int numError = new OrderHeaderTransition(ase).update(
                new OrderHeaderContext(directOption("02", "ROLPAGO"), "ROLPAGO", "CUE", "N", 0, null));

        assertThat(numError).isZero();
    }

    // ---- NULL service ------------------------------------------------------------------------

    @Test
    @DisplayName("RULE-010: a NULL service with act_totord 'S' -> 'NULL not in (...)' is not true -> no 122004")
    void rule010_nullServiceNeverRaises122004() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        int numError = new OrderHeaderTransition(ase).update(
                new OrderHeaderContext(directOption("02", null), null, "CUE", "S", 0, null));

        assertThat(numError).isZero();
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::rowCount).containsExactly(0, 0);
    }

    @Test
    @DisplayName("RULE-010: end to end, a request with a NULL service commits (0, 0) with no header moved")
    void rule010_nullServiceEndToEndCommits() {
        FakeAseSession ase = new FakeAseSession().headerRow(ORDER, "CUE", "ROLPAGO", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio(null).build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerState(ORDER, "CUE", "ROLPAGO")).isEqualTo("I");
        assertThat(ase.commits()).isEqualTo(1);
    }

    // ---- stale @wRowdbBiz --------------------------------------------------------------------

    @ParameterizedTest(name = "RULE-010 + RULE-014: direct channel + option ''{0}'' -> no UPDATE, stale @wRowdbBiz NULL, no 122004, commit")
    @ValueSource(strings = {"04", "00", "1", "2", "ZZ", " 01"})
    void rule010_rule014_optionOutsideRangeRunsNoUpdateAndCommits(String option) {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(directOption(option, "ROLPAGO"));

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerUpdates()).isEmpty();
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).as("@wRowdbBiz before B12 (line 140: NULL)").isNull();
        assertThat(ase.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-010 + RULE-014: direct channel + NULL option -> no UPDATE, no 122004, commit")
    void rule010_rule014_nullOptionRunsNoUpdate() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(directOption(null, "ROLPAGO"));

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerUpdates()).isEmpty();
    }

    @Test
    @DisplayName("RULE-010 (stale @wRowdbBiz): option '04' with a prior rowcount of 0 -> 122004 without any UPDATE")
    void rule010_staleZeroRowCountRaises122004() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        int numError = new OrderHeaderTransition(ase).update(
                new OrderHeaderContext(directOption("04", "ROLPAGO"), "ROLPAGO", "CUE", "S", 0, 0));

        assertThat(numError).isEqualTo(122004);
        assertThat(ase.headerUpdates()).isEmpty();
    }

    @Test
    @DisplayName("RULE-010 (stale @wRowdbBiz): option '04' with prior rowcount NULL or 1 -> 0")
    void rule010_staleNullOrPositiveRowCountContinues() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();
        OrderHeaderTransition step = new OrderHeaderTransition(ase);
        DebitRequest request = directOption("04", "ROLPAGO");

        assertThat(step.update(new OrderHeaderContext(request, "ROLPAGO", "CUE", "S", 0, null))).isZero();
        assertThat(step.update(new OrderHeaderContext(request, "ROLPAGO", "CUE", "S", 0, 1))).isZero();
    }

    @Test
    @DisplayName("RULE-010 (stale @wRowdbBiz): a prior rowcount 0 still honors the exemptions (exempt service, act_totord 'N')")
    void rule010_staleZeroRowCountStillHonorsExemptions() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();
        OrderHeaderTransition step = new OrderHeaderTransition(ase);

        assertThat(step.update(new OrderHeaderContext(directOption("04", "TRANSWIFT"), "TRANSWIFT", "CUE", "S", 0, 0))).isZero();
        assertThat(step.update(new OrderHeaderContext(directOption("04", "ROLPAGO"), "ROLPAGO", "CUE", "N", 0, 0))).isZero();
    }

    @Test
    @DisplayName("RULE-010: once an UPDATE runs its rowcount replaces the prior value (prior 5, 0 rows -> 122004; prior 0, 1 row -> 0)")
    void rule010_updateOverwritesThePriorRowCount() {
        FakeAseSession empty = new FakeAseSession().noHeaderRows();
        FakeAseSession oneRow = new FakeAseSession().headerRow(ORDER, "CUE", "ROLPAGO", "I");

        assertThat(new OrderHeaderTransition(empty).update(
                new OrderHeaderContext(directOption("02", "ROLPAGO"), "ROLPAGO", "CUE", "S", 0, 5))).isEqualTo(122004);
        assertThat(new OrderHeaderTransition(oneRow).update(
                new OrderHeaderContext(directOption("02", "ROLPAGO"), "ROLPAGO", "CUE", "S", 0, 0))).isZero();
    }

    @Test
    @DisplayName("RULE-010 + RULE-014: another channel ignores the option: option '04' still updates and can raise 122004")
    void rule010_rule014_otherChannelIgnoresOption() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        DebitResult result = ase.service().debit(Requests.currentAccount().canal("WEB").opcion("04").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.headerUpdates()).hasSize(2);
    }

    // ---- @@error parity (approved at the plan gate) -----------------------------------------

    @Test
    @DisplayName("RULE-010 (@@error parity): live UPDATE fails -> counted as 0 rows -> history moves its row -> commit")
    void rule010_liveUpdateErrorFallsBackToHistory() {
        FakeAseSession ase = new FakeAseSession().headerUpdateThrows(HeaderTable.LIVE)
                .historyHeaderRow(ORDER, "CUE", "ROLPAGO", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.historyHeaderState(ORDER, "CUE", "ROLPAGO")).isEqualTo("T");
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::rowCount).containsExactly(-1, 1);
        assertThat(ase.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-010 (@@error parity): live UPDATE fails and history has nothing -> 122004")
    void rule010_liveUpdateErrorWithEmptyHistoryGives122004() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows().headerUpdateThrows(HeaderTable.LIVE);

        DebitResult result = ase.service().debit(Requests.currentAccount().build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.rollbacks()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-010 (@@error parity): history UPDATE fails after live 0 -> 0 rows -> 122004 (COBIS: sp_cerror)")
    void rule010_historyUpdateErrorGives122004() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows().headerUpdateThrows(HeaderTable.HISTORY);

        DebitResult result = ase.service().debit(Requests.currentAccount().aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(122004, 0, null));
        assertThat(ase.onlyErrorReport().num()).isEqualTo(122004);
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::rowCount).containsExactly(0, -1);
    }

    @Test
    @DisplayName("RULE-010 (@@error parity): both UPDATEs fail -> 122004; an exempt service still commits")
    void rule010_bothUpdatesFail() {
        FakeAseSession rolpago = new FakeAseSession().headerUpdateThrows(HeaderTable.LIVE).headerUpdateThrows(HeaderTable.HISTORY);
        FakeAseSession transwift = new FakeAseSession().headerUpdateThrows(HeaderTable.LIVE).headerUpdateThrows(HeaderTable.HISTORY);

        assertThat(rolpago.service().debit(Requests.currentAccount().build())).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(transwift.service().debit(Requests.currentAccount().servicio("TRANSWIFT").build()))
                .isEqualTo(new DebitResult(0, 0, null));
        assertThat(transwift.commits()).isEqualTo(1);
    }
}
