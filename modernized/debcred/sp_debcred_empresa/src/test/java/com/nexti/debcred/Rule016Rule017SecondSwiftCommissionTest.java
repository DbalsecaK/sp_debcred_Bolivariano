package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B11 (lines 1742-1856): the second, SWIFT commission. RULE-016: only for a trimmed service
 * TRANSWIFT with {@code isnull(@i_valor2_swift, 0) > 0}, charged with affectation '16', the
 * request's own reference, tariff = the SWIFT value, no other REF33 field. RULE-017: its failure is
 * {@code goto lbl_error} (exit C: full rollback, code 122003 or the procedure's own); the commit
 * branch at 1844-1854 can never run.
 */
class Rule016Rule017SecondSwiftCommissionTest {

    private static Requests swift() {
        return Requests.currentAccount().servicio("TRANSWIFT").codSwift("ABCDEC2X").valor2Swift("15.00");
    }

    @Test
    @DisplayName("RULE-016: TRANSWIFT with valor2_swift 15.00 after a 2.00 commission -> second sp_grb_comision with every legacy argument (1748-1820): tipoafec '16', request reference, tarifa 15.00, other REF33 null")
    void rule016_secondCommissionArgumentsInLegacyOrder() {
        FakeAseSession ase = new FakeAseSession().debitNote(0, 9001);
        DebitRequest request = swift()
                .sSsn(77).sSrv("SRVTEST").sUser("usrtest").sTerm("TERM01").sOfi(1).aplcobis("N").spName("sp_test_caller")
                .canal("DIR").empresa(500).producto(1).tipoProceso("L").orden(123456).tarjeta(null).frmPagcob("CUE")
                .monDebito(1).tipctaEmp(3).numctaEmp(Requests.ACCOUNT).referencia("REF TEST 001").refProv("PROV-REF-01")
                .tipoPagcob("P").paisCta(1).codBancoCta(34).nemEmp("EMPTEST").localidadOrden(1).ordenEmpresa(4587)
                .tipoReferencia("N").comision("0").valorComision("2.00")
                .valorTarifa("1.00").valorComisionCue("0.10").valorTarifaEfe("1.50").valorComisionEfe("0.20")
                .valorTarifaChe("1.75").valorComisionChe("0.30")
                .build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        List<CommissionCommand> charged = ase.commissionCommands();
        assertThat(charged).hasSize(2);
        assertThat(charged.get(0).iTipoafec()).isNull();
        assertThat(charged.get(0).iValorComision()).isEqualByComparingTo("2.00");
        assertThat(charged.get(0).iValorTarifa()).isEqualByComparingTo("1.00");
        assertThat(charged.get(0).iReferencia()).isEqualTo("COBRO DE COMISION");
        assertThat(charged.get(1)).isEqualTo(new CommissionCommand(
                77, "SRVTEST", "usrtest", "TERM01", 1, "N", "sp_test_caller", Requests.PROCESS_DATE, "DIR", 500, 1,
                "TRANSWIFT", "L", 123456, new BigDecimal("15.00"), "COD:ABCDEC2X", null, "CUE", 1, 3, Requests.ACCOUNT,
                "REF TEST 001", "PROV-REF-01", "P", 1, 34, "EMPTEST", 1, null, null, 4587, "N",
                "16", "sp_debito_empresa", 0,
                new BigDecimal("15.00"), null, null, null, null, null));
        assertThat(ase.calls()).endsWith("sp_grb_mov_y_frmpgo", "commissionStep", "sp_grb_comision", "sp_grb_comision",
                "orderHeaderStep", "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo", "sp_grb_comision",
                "sp_grb_comision");
    }

    @Test
    @DisplayName("RULE-016: no separate commission (0) but valor2_swift 15.00 -> only the SWIFT call runs (blocks are independent)")
    void rule016_swiftCommissionWithoutFirstCommission() {
        FakeAseSession ase = new FakeAseSession();

        DebitResult result = ase.service().debit(swift().comision("0").valorComision("0").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        CommissionCommand only = ase.onlyCommissionCommand();
        assertThat(only.iTipoafec()).isEqualTo("16");
        assertThat(only.iValorComision()).isEqualByComparingTo("15.00");
        assertThat(only.iValorTarifa()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("RULE-016: the SWIFT value is not charged for a non-TRANSWIFT service (valor2_swift 15.00 on ROLPAGO is ignored here)")
    void rule016_otherServicesIgnoreTheSwiftValue() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").valor2Swift("15.00").valorComision("2.00").build());

        assertThat(ase.commissionCommands()).hasSize(1);
        assertThat(ase.onlyCommissionCommand().iTipoafec()).isNull();
    }

    @Test
    @DisplayName("RULE-016: TRANSWIFT with valor2_swift 0, NULL (-> 0 by default, isnull) or negative -> no SWIFT call")
    void rule016_zeroNullOrNegativeSwiftValueSkipsTheCall() {
        FakeAseSession zero = new FakeAseSession();
        FakeAseSession none = new FakeAseSession();
        FakeAseSession negative = new FakeAseSession();

        zero.service().debit(swift().valor2Swift("0").valorComision("0").build());
        none.service().debit(swift().valor2Swift(null).valorComision("0").build());
        negative.service().debit(swift().valor2Swift("-1.00").valorComision("0").build());

        assertThat(zero.commissionCommands()).isEmpty();
        assertThat(none.commissionCommands()).isEmpty();
        assertThat(negative.commissionCommands()).isEmpty();
    }

    @Test
    @DisplayName("RULE-016: the service test is ltrim(rtrim(...)): ' TRANSWIFT ' matches (line 1742)")
    void rule016_serviceMatchIgnoresLeadingAndTrailingBlanks() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(swift().servicio(" TRANSWIFT ").valorComision("0").build());

        assertThat(ase.onlyCommissionCommand().iTipoafec()).isEqualTo("16");
        assertThat(ase.onlyCommissionCommand().iServicio()).as("the service is passed as received").isEqualTo(" TRANSWIFT ");
    }

    @Test
    @DisplayName("RULE-017 exit C, aplcobis 'N': SWIFT commission returns @o_error 122010 -> full rollback, return 0 / o_error 122010, nothing committed, no sp_cerror, no 'X' movement")
    void rule017_swiftFailureIsExitCNotCobis() {
        FakeAseSession ase = new FakeAseSession().secondCommission(0, 122010);

        DebitResult result = ase.service().debit(swift().valorComision("2.00").aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122010, null));
        assertThat(ase.calls()).endsWith("sp_grb_mov_y_frmpgo", "commissionStep", "sp_grb_comision", "sp_grb_comision", "rollback");
        assertThat(ase.committedWrites()).as("the debit, the movement and both commissions are gone").isEmpty();
        assertThat(ase.movements()).as("unlike exit B, no 'X' movement is written here").hasSize(1);
        assertThat(ase.commits()).isZero();
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.orderHeaderSteps()).isEmpty();
        assertThat(ase.transactionOpen()).isFalse();
    }

    @Test
    @DisplayName("RULE-017 exit C, aplcobis 'S': rollback, sp_cerror(sp_name, 122010), return 122010, o_error stays 0")
    void rule017_swiftFailureFromCobisReportsAndReturnsTheCode() {
        FakeAseSession ase = new FakeAseSession().secondCommission(0, 122010);

        DebitResult result = ase.service().debit(swift().valorComision("2.00").aplcobis("S").spName("sp_test_caller").build());

        assertThat(result).isEqualTo(new DebitResult(122010, 0, null));
        assertThat(ase.onlyErrorReport()).isEqualTo(new ErrorReport("sp_test_caller", 122010));
        assertThat(ase.calls()).endsWith("sp_grb_comision", "rollback", "sp_cerror");
        assertThat(ase.committedWrites()).isEmpty();
        assertThat(ase.commits()).isZero();
    }

    @Test
    @DisplayName("RULE-017: non-zero return value with @o_error 0 -> the generic 122003 (line 1830), exit C")
    void rule017_returnValueOnlyFailureIs122003() {
        FakeAseSession n = new FakeAseSession().secondCommission(1, 0);
        FakeAseSession s = new FakeAseSession().secondCommission(1, 0);

        DebitResult resultN = n.service().debit(swift().valorComision("2.00").aplcobis("N").build());
        DebitResult resultS = s.service().debit(swift().valorComision("2.00").aplcobis("S").build());

        assertThat(resultN).isEqualTo(new DebitResult(0, 122003, null));
        assertThat(resultS).isEqualTo(new DebitResult(122003, 0, null));
        assertThat(s.onlyErrorReport().num()).isEqualTo(122003);
        assertThat(n.committedWrites()).isEmpty();
    }

    @Test
    @DisplayName("RULE-017: a SQL error (@@error) on the SWIFT call is 122003 via lbl_error")
    void rule017_sqlErrorOnSwiftCallIs122003() {
        FakeAseSession ase = new FakeAseSession().secondCommissionThrows();

        DebitResult result = ase.service().debit(swift().valorComision("2.00").aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122003, null));
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.committedWrites()).isEmpty();
    }

    @Test
    @DisplayName("RULE-017: non-zero return value AND @o_error 122010 -> 122010 wins (line 1832)")
    void rule017_oErrorWinsOverTheGenericCode() {
        FakeAseSession ase = new FakeAseSession().secondCommission(1, 122010);

        DebitResult result = ase.service().debit(swift().valorComision("2.00").aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122010, null));
    }

    @Test
    @DisplayName("RULE-017: lines 1844-1854 are dead: a failed SWIFT commission never reaches 'commit tran' in either aplcobis mode (goto lbl_error fires first)")
    void rule017_unreachableCommitOnSwiftCommissionError() {
        FakeAseSession n = new FakeAseSession().secondCommission(0, 122010);
        FakeAseSession s = new FakeAseSession().secondCommission(0, 122010);

        n.service().debit(swift().valorComision("2.00").aplcobis("N").build());
        s.service().debit(swift().valorComision("2.00").aplcobis("S").build());

        assertThat(n.calls()).doesNotContain("commit");
        assertThat(s.calls()).doesNotContain("commit");
        assertThat(n.calls()).containsOnlyOnce("rollback");
        assertThat(s.calls()).containsOnlyOnce("rollback");
    }

    @Test
    @DisplayName("RULE-016 + RULE-013: a successful SWIFT commission reaches the order-header step and commits; both commission writes survive")
    void rule016_happyPathCommitsBothCommissions() {
        FakeAseSession ase = new FakeAseSession().commission(0, 0).secondCommission(0, 0);

        DebitResult result = ase.service().debit(swift().valorComision("2.00").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().servicio()).isEqualTo("TRANSWIFT");
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo", "sp_grb_comision",
                "sp_grb_comision");
    }
}
