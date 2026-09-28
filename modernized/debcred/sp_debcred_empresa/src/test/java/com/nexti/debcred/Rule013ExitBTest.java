package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Exit B (lines 1606-1700, REF44), RULE-013 P0: the first commission failed. The procedure rolls
 * the WHOLE transaction back (debit note and 'P' movement included), writes a commission movement
 * with status 'X' in autocommit, and returns {@code @w_cod_errord} as return value and
 * {@code @o_error} without committing and without {@code sp_cerror}. Brief section 7 A14 (approved
 * as parity): when the failure leaves {@code @w_cod_errord = 0} (non-zero return value or
 * {@code @@error} only) the caller receives 0 / 0 after everything was rolled back. Lines 1716-1730
 * are unreachable: no test in this class ever sees a commit.
 */
class Rule013ExitBTest {

    private static Requests withCommission() {
        return Requests.currentAccount().comision("1.50").valorComision("2.00");
    }

    @Test
    @DisplayName("RULE-013 (P0) exit B via @o_error 122010: rollback tran, 'X' movement written after it, return 122010 / o_error 122010, no commit, no sp_cerror")
    void rule013_exitB_oErrorRollsBackEverythingAndRecordsTheFailure() {
        FakeAseSession ase = new FakeAseSession().debitNote(0, 9001).commission(0, 122010);
        DebitRequest request = withCommission().aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "notificationStep",
                "sp_grb_mov_y_frmpgo",
                "commissionStep",
                "sp_grb_comision",
                "rollback",
                "sp_grb_mov_y_frmpgo");
        assertThat(ase.commits()).as("lines 1716-1730 are unreachable: exit B never commits").isZero();
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.transactionOpen()).isFalse();
        // committed state: the debit note, the 'P' movement and the commission's own write are gone
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.committedWrites().get(0).payload()).isSameAs(ase.lastMovement());
        assertThat(ase.movements()).hasSize(2);
        assertThat(ase.movements().get(0).iEstProceso()).isEqualTo("P");
        assertThat(ase.lastMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.orderHeaderSteps()).as("no order-header step after exit B").isEmpty();
        assertThat(ase.errorReports()).isEmpty();
    }

    @Test
    @DisplayName("RULE-013: the exit-B movement carries every legacy argument (lines 1628-1694): 'X', cod_error, SPI-swappable form, valor_mov = @i_valor_comision, 'COBRO DE COMISION', valor_comision = @i_comision, tran_ncnd of the debit")
    void rule013_exitBMovementArguments() {
        FakeAseSession ase = new FakeAseSession().debitNote(0, 9001).commission(0, 122010);
        DebitRequest request = withCommission()
                .sUser("usrtest").sTerm("TERM01").sOfi(5).tipoProceso("L").empresa(500).producto(1).orden(123456)
                .canal("DIR").frmPagcob("CUE").monDebito(1).valorDebito("100.00").referencia("REF TEST 001")
                .servicio("ROLPAGO").tipoPagcob("P").paisCta(1).codBancoCta(34).tipctaEmp(3).numctaEmp(Requests.ACCOUNT)
                .valorOrdenado("100.00").nemEmp("EMPTEST").localidadOrden(1).ordenEmpresa(4587).build();

        ase.service().debit(request);

        assertThat(ase.lastMovement()).isEqualTo(new MovementCommand(
                "usrtest", "TERM01", 5, 2701, "L", 500, 1, 123456, "DIR", "0150", "X", 122010, "CUE", 1,
                new BigDecimal("2.00"), "1", Requests.PROCESS_DATE, "COBRO DE COMISION", 0, "ROLPAGO", "P", 1, 34, 3,
                Requests.ACCOUNT, new BigDecimal("100.00"), "EMPTEST", 1, null, null, 4587, new BigDecimal("1.50"),
                9001));
    }

    @Test
    @DisplayName("RULE-013 + RULE-027: exit-B movement after the bundled 2.50 was moved: valor_mov 2.50, valor_comision 0")
    void rule013_exitBMovementUsesTheNormalizedValues() {
        FakeAseSession ase = new FakeAseSession().commission(0, 122010);

        ase.service().debit(Requests.currentAccount().comision("2.50").valorComision("0").build());

        assertThat(ase.lastMovement().iValorMov()).isEqualByComparingTo("2.50");
        assertThat(ase.lastMovement().iValorComision()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RULE-013 exit B from COBIS (aplcobis 'S'): same convention, code in return AND o_error, no sp_cerror (line 1700 returns before lbl_error)")
    void rule013_exitBFromCobisDoesNotReport() {
        FakeAseSession ase = new FakeAseSession().commission(0, 122010);

        DebitResult result = ase.service().debit(withCommission().aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.commits()).isZero();
        assertThat(ase.rollbacks()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-013 A14 (approved as-is): sp_grb_comision returns 1 with @o_error 0 -> rollback, 'X' movement with cod_error 0, return 0 / o_error 0: the caller sees success with no debit")
    void rule013_a14_returnValueOnlyFailureGivesZeroZeroAfterRollback() {
        FakeAseSession ase = new FakeAseSession().commission(1, 0);

        DebitResult result = ase.service().debit(withCommission().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.commits()).isZero();
        assertThat(ase.lastMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.lastMovement().iCodError()).isEqualTo(0);
        assertThat(ase.committedProcedures()).as("the debit did not survive").containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.orderHeaderSteps()).isEmpty();
    }

    @Test
    @DisplayName("RULE-013 A14: an unassigned @o_error output (null) reads as the 0 it held -> same 0 / 0 exit B")
    void rule013_a14_nullOErrorReadsAsZero() {
        FakeAseSession ase = new FakeAseSession().commission(1, null);

        DebitResult result = ase.service().debit(withCommission().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.lastMovement().iCodError()).isEqualTo(0);
        assertThat(ase.rollbacks()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-013 A14: a SQL error (@@error) on sp_grb_comision is exit B with @w_cod_errord still 0 -> 0 / 0, rollback, 'X' movement with cod_error 0")
    void rule013_a14_sqlErrorOnCommissionIsExitBWithZero() {
        FakeAseSession ase = new FakeAseSession().commissionThrows();

        DebitResult result = ase.service().debit(withCommission().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.calls()).endsWith("commissionStep", "sp_grb_comision", "rollback", "sp_grb_mov_y_frmpgo");
        assertThat(ase.lastMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.lastMovement().iCodError()).isEqualTo(0);
        assertThat(ase.lastMovement().iReferencia()).isEqualTo("COBRO DE COMISION");
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.commits()).isZero();
    }

    @Test
    @DisplayName("RULE-013: non-zero return value AND @o_error 122010 -> the movement and the result carry 122010 (@w_cod_errord wins, line 1614)")
    void rule013_returnValueAndOErrorBothSet() {
        FakeAseSession ase = new FakeAseSession().commission(1, 122010);

        DebitResult result = ase.service().debit(withCommission().build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.lastMovement().iCodError()).isEqualTo(122010);
    }

    @Test
    @DisplayName("RULE-013: the return value of the exit-B movement write is ignored (line 1628 has no check): the result is unchanged, nothing survives")
    void rule013_exitBMovementReturnIsIgnored() {
        FakeAseSession ase = new FakeAseSession().commission(0, 122010).secondMovement(5);

        DebitResult result = ase.service().debit(withCommission().aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.movements()).hasSize(2);
        assertThat(ase.committedWrites()).as("the 'X' write failed and nothing else was committed").isEmpty();
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.commits()).isZero();
    }

    @Test
    @DisplayName("RULE-013: exit B for a virtual account (type 12): the sp_vi_ndc_automatica write is gone, tran_ncnd 9002 on the 'X' movement")
    void rule013_exitBVirtualAccount() {
        FakeAseSession ase = new FakeAseSession().virtualDebit(0, 0, 9002).commission(0, 122010);

        DebitResult result = ase.service().debit(withCommission().tipctaEmp(12).build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.lastMovement().iTranNcnd()).isEqualTo(9002);
        assertThat(ase.lastMovement().iTipoCta()).isEqualTo(12);
    }

    @Test
    @DisplayName("RULE-013: exit B for an accounting account (type 9): the ledger write is gone, tran_ncnd NULL, cadena NULL on the commission")
    void rule013_exitBAccountingAccount() {
        FakeAseSession ase = new FakeAseSession().commission(0, 122010);

        DebitResult result = ase.service().debit(withCommission().tipctaEmp(9).build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.lastMovement().iTranNcnd()).isNull();
        assertThat(ase.onlyCommissionCommand().iCadena()).isNull();
    }

    @Test
    @DisplayName("RULE-013 + RULE-016: a failed first commission on TRANSWIFT with valor2_swift 15.00 takes exit B before the SWIFT call: one sp_grb_comision only")
    void rule013_exitBPreemptsTheSwiftCommission() {
        FakeAseSession ase = new FakeAseSession().commission(0, 122010);

        DebitResult result = ase.service().debit(withCommission().servicio("TRANSWIFT").codSwift("ABCDEC2X")
                .valor2Swift("15.00").build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.commissionCommands()).hasSize(1);
        assertThat(ase.onlyCommissionCommand().iTipoafec()).isNull();
    }

    @Test
    @DisplayName("RULE-013: lines 1716-1730 are dead: with @o_error != 0 the flow never reaches 'commit tran' (the transaction ends by rollback only)")
    void rule013_unreachableCommitOnCommissionError() {
        FakeAseSession n = new FakeAseSession().commission(0, 122010);
        FakeAseSession s = new FakeAseSession().commission(0, 122010);

        n.service().debit(withCommission().aplcobis("N").build());
        s.service().debit(withCommission().aplcobis("S").build());

        assertThat(n.calls()).doesNotContain("commit");
        assertThat(s.calls()).doesNotContain("commit");
        assertThat(n.calls()).containsOnlyOnce("rollback");
        assertThat(s.calls()).containsOnlyOnce("rollback");
    }
}
