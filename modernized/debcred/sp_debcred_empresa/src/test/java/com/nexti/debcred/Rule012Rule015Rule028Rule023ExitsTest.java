package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Blocks B9, B14, B15 (lines 1332-1492, 2130-2170): the three exits pinned on committed state.
 * Exit A (RULE-012 confirmed as-is, RULE-015): debit failed -> rollback to the savepoint, movement
 * 'X' with the error code, commit, error returned as return code AND o_error, no sp_cerror.
 * Exit C (RULE-028): lbl_error -> full rollback, sp_cerror only when aplcobis = 'S'.
 * Success: commit, return 0, o_error 0. Plus the SPI-return service lookup (RULE-023, RULE-024)
 * and the full movement argument list.
 */
class Rule012Rule015Rule028Rule023ExitsTest {

    @Test
    @DisplayName("RULE-012 (P0) + RULE-015 exit A: sp_ndc_ahcc returns 201045 -> rollbackToSavepoint, movement 'X'/201045 written after it, commit, return 201045 / o_error 201045")
    void rule012_rule015_debitNoteFailureIsCommittedAsRecordedFailure() {
        FakeAseSession ase = new FakeAseSession().debitNote(201045, null);
        DebitRequest request = Requests.currentAccount().tipctaEmp(3).aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(201045, 201045, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "rollbackToSavepoint:sp_debito_empresa",
                "sp_grb_mov_y_frmpgo",
                "commit");
        MovementCommand movement = ase.onlyMovement();
        assertThat(movement.iEstProceso()).isEqualTo("X");
        assertThat(movement.iCodError()).isEqualTo(201045);
        assertThat(movement.iTranNcnd()).isNull();
        // committed state: only the error movement survives; no debit note, nothing else ran
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.transactionOpen()).isFalse();
        assertThat(ase.notifications()).as("notification runs only when the debit returned 0").isEmpty();
        assertThat(ase.commissionSteps()).isEmpty();
        assertThat(ase.orderHeaderSteps()).isEmpty();
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.rollbacks()).as("exit A never does a full rollback").isZero();
    }

    @Test
    @DisplayName("RULE-015 exit A with aplcobis 'S': same convention (code in return and o_error), no sp_cerror")
    void rule015_exitAFromCobisDoesNotReport() {
        FakeAseSession ase = new FakeAseSession().debitNote(201045, null);

        DebitResult result = ase.service().debit(Requests.currentAccount().aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(201045, 201045, null));
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-012: rows the failing debit procedure wrote before returning its error are gone after exit A (savepoint rollback), the 'X' movement is not")
    void rule012_savepointRollbackDiscardsTheDebitWrite() {
        // The fake records a write for every debit procedure call, error or not (a real procedure may
        // insert before failing). Only the savepoint rollback can make that write disappear while the
        // movement written afterwards survives the commit. Both paths (sp_ndc_ahcc, sp_vi_ndc_automatica):
        FakeAseSession current = new FakeAseSession().debitNote(201045, null);
        FakeAseSession virtual = new FakeAseSession().virtualDebit(201045, 0, 5);

        current.service().debit(Requests.currentAccount().tipctaEmp(3).build());
        virtual.service().debit(Requests.currentAccount().tipctaEmp(12).build());

        assertThat(current.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(virtual.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-015 + RULE-028 exit C: movement write returns 5 -> 122002, full rollback, aplcobis 'N' -> return 0 / o_error 122002, nothing committed")
    void rule015_rule028_movementWriteFailureIs122002FullRollback() {
        FakeAseSession ase = new FakeAseSession().movement(5);
        DebitRequest request = Requests.currentAccount().aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 122002, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision", "sp_con_confcontable", "begin", "savepoint:sp_debito_empresa", "sp_ndc_ahcc",
                "notificationStep", "sp_grb_mov_y_frmpgo", "rollback");
        assertThat(ase.committedWrites()).as("the debit note must not survive").isEmpty();
        assertThat(ase.commits()).isZero();
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.commissionSteps()).isEmpty();
    }

    @Test
    @DisplayName("RULE-028 exit C from COBIS: 122002 -> rollback, sp_cerror(sp_name, 122002), return 122002, o_error stays 0")
    void rule028_movementWriteFailureFromCobisReportsAndReturnsCode() {
        FakeAseSession ase = new FakeAseSession().movement(5);
        DebitRequest request = Requests.currentAccount().aplcobis("S").spName("sp_test_caller").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(122002, 0, null));
        assertThat(ase.onlyErrorReport()).isEqualTo(new ErrorReport("sp_test_caller", 122002));
        assertThat(ase.calls()).endsWith("sp_grb_mov_y_frmpgo", "rollback", "sp_cerror");
        assertThat(ase.committedWrites()).isEmpty();
    }

    @Test
    @DisplayName("RULE-015: movement write failure after a FAILED debit (122001) is still exit C 122002, not exit A")
    void rule015_movementFailureAfterDebitFailureIsExitC() {
        FakeAseSession ase = new FakeAseSession().movement(5);

        DebitResult result = ase.service().debit(Requests.currentAccount().tipctaEmp(7).aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122002, null));
        assertThat(ase.calls()).containsSubsequence("rollbackToSavepoint:sp_debito_empresa", "sp_grb_mov_y_frmpgo", "rollback");
        assertThat(ase.commits()).isZero();
    }

    @Test
    @DisplayName("RULE-015: a SQL error (@@error) on the movement write is also 122002")
    void rule015_sqlErrorOnMovementIs122002() {
        FakeAseSession ase = new FakeAseSession().movementThrows();

        DebitResult result = ase.service().debit(Requests.currentAccount().aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122002, null));
        assertThat(ase.rollbacks()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-012: movement record carries every legacy argument on the happy path (lines 1394-1460)")
    void rule012_movementArgumentsHappyPath() {
        FakeAseSession ase = new FakeAseSession().debitNote(0, 9001);
        DebitRequest request = Requests.currentAccount()
                .sUser("usrtest").sTerm("TERM01").sOfi(5).tipoProceso("L").empresa(500).producto(1).orden(123456)
                .canal("DIR").frmPagcob("CUE").monDebito(1).valorDebito("100.00").referencia("REF TEST 001")
                .servicio("ROLPAGO").tipoPagcob("P").paisCta(1).codBancoCta(34).tipctaEmp(3).numctaEmp(Requests.ACCOUNT)
                .valorOrdenado("100.00").nemEmp("EMPTEST").localidadOrden(1).ordenEmpresa(4587)
                .comision("1.50").valorComision("2.00").build();

        ase.service().debit(request);

        assertThat(ase.onlyMovement()).isEqualTo(new MovementCommand(
                "usrtest", "TERM01", 5, 2701, "L", 500, 1, 123456, "DIR", "0150", "P", 0, "CUE", 1,
                new BigDecimal("100.00"), "1", Requests.PROCESS_DATE, "REF TEST 001", 0, "ROLPAGO", "P", 1, 34, 3,
                Requests.ACCOUNT, new BigDecimal("100.00"), "EMPTEST", 1, null, null, 4587, new BigDecimal("1.50"),
                9001));
    }

    @Test
    @DisplayName("Success exit (line 2130-2136): commit, return 0, o_error 0, o_reg_a_proc null; commission and header stubs reached with the working values")
    void successExitCommitsAndReachesLaterPhaseStubs() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().comision("2.50").valorComision("0").frmPagcob("CUE")
                .frmPagcobDeb(null).build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(result.oRegAProc()).isNull();
        CommissionContext commission = ase.onlyCommissionStep();
        assertThat(commission.request()).isSameAs(request);
        assertThat(commission.savepoint()).isEqualTo("sp_debito_empresa");
        assertThat(commission.trn()).isEqualTo(2701);
        assertThat(commission.causal()).isEqualTo("0150");
        assertThat(commission.frmPagcob()).isEqualTo("CUE");
        OrderHeaderContext header = ase.onlyOrderHeaderStep();
        assertThat(header.servicio()).isEqualTo("ROLPAGO");
        assertThat(header.frmPagcobDeb()).as("@i_frm_pagcob_deb defaults to @i_frm_pagcob (line 262)").isEqualTo("CUE");
        assertThat(header.actTotord()).isEqualTo("S");
        assertThat(header.codErrord()).isEqualTo(0);
        assertThat(ase.calls()).endsWith("sp_grb_mov_y_frmpgo", "commissionStep", "orderHeaderStep", "commit");
    }

    @Test
    @DisplayName("Explicit @i_frm_pagcob_deb 'EFE' is kept for the header step")
    void explicitDebitPaymentFormIsKept() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().frmPagcob("CUE").frmPagcobDeb("EFE").build());

        assertThat(ase.onlyOrderHeaderStep().frmPagcobDeb()).isEqualTo("EFE");
    }

    @Test
    @DisplayName("RULE-023 + RULE-024: SPI return (afec 12) -> movement service = live bp_orden service, act_totord 'N', history not read")
    void rule023_rule024_spiReturnUsesLiveOrderService() {
        FakeAseSession ase = new FakeAseSession().liveOrder(Requests.BANK_ORDER, "TRANSCLI")
                .historyOrder(Requests.BANK_ORDER, "SHOULD-NOT-BE-READ");

        ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").orden(Requests.BANK_ORDER).build());

        assertThat(ase.onlyMovement().iServicio()).isEqualTo("TRANSCLI");
        assertThat(ase.liveOrderLookups()).containsExactly(Requests.BANK_ORDER);
        assertThat(ase.historyOrderLookups()).isEmpty();
        assertThat(ase.onlyOrderHeaderStep().servicio()).isEqualTo("TRANSCLI");
        assertThat(ase.onlyOrderHeaderStep().actTotord()).isEqualTo("N");
        // the lookup happens after the debit outcome and before the movement
        assertThat(ase.calls()).containsSubsequence("sp_ndc_ahcc", "bp_orden:" + Requests.BANK_ORDER, "sp_grb_mov_y_frmpgo");
        // the debit note itself still went out under service 'SPI'
        assertThat(ase.onlyDebitNote().iServicio()).isEqualTo("SPI");
    }

    @Test
    @DisplayName("RULE-024: SPI return not in bp_orden -> db_sat_his..bp_orden_his is read (lines 1360-1370)")
    void rule024_spiReturnFallsBackToHistory() {
        FakeAseSession ase = new FakeAseSession().historyOrder(Requests.BANK_ORDER, "TARJCRED");

        ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());

        assertThat(ase.liveOrderLookups()).containsExactly(Requests.BANK_ORDER);
        assertThat(ase.historyOrderLookups()).containsExactly(Requests.BANK_ORDER);
        assertThat(ase.onlyMovement().iServicio()).isEqualTo("TARJCRED");
    }

    @Test
    @DisplayName("RULE-024: SPI return found nowhere -> movement service NULL, act_totord still 'N'")
    void rule024_spiReturnUnknownOrderGivesNullService() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());

        assertThat(ase.onlyMovement().iServicio()).isNull();
        assertThat(ase.onlyOrderHeaderStep().actTotord()).isEqualTo("N");
    }

    @Test
    @DisplayName("RULE-023: SPI return lookup also runs on exit A (after the savepoint rollback, before the 'X' movement)")
    void rule023_spiReturnLookupRunsOnExitA() {
        FakeAseSession ase = new FakeAseSession().debitNote(201045, null).liveOrder(Requests.BANK_ORDER, "TRANSCLI");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());

        assertThat(result).isEqualTo(new DebitResult(201045, 201045, null));
        assertThat(ase.onlyMovement().iServicio()).isEqualTo("TRANSCLI");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
    }

    @Test
    @DisplayName("RULE-023: SPI with affectation '10' and non-SPI with '12' do not look the order up")
    void rule023_onlySpiWithAffectation12LooksUp() {
        FakeAseSession spi10 = new FakeAseSession().liveOrder(Requests.BANK_ORDER, "TRANSCLI");
        FakeAseSession rol12 = new FakeAseSession().liveOrder(Requests.BANK_ORDER, "TRANSCLI");

        spi10.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("10").build());
        rol12.service().debit(Requests.currentAccount().servicio("ROLPAGO").tipoAfec("12").build());

        assertThat(spi10.liveOrderLookups()).isEmpty();
        assertThat(rol12.liveOrderLookups()).isEmpty();
        assertThat(spi10.onlyMovement().iServicio()).isEqualTo("SPI");
        assertThat(rol12.onlyMovement().iServicio()).isEqualTo("ROLPAGO");
        assertThat(spi10.onlyOrderHeaderStep().actTotord()).isEqualTo("S");
    }
}
