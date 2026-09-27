package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B6 (lines 414-740) and the rejection branch (1308-1318): the debit is routed by company
 * account type (RULE-009, P0): 3/4 through sp_ndc_ahcc, 12 through sp_vi_ndc_automatica with
 * channel 'SAT', office 0, state check 'S' and the batch flag (RULE-026, RULE-033), and any other
 * type is 122001 with the movement still written with status 'X' (RULE-008, P0, confirmed as-is).
 * The first slice of Phase 1 is the account-type-3 happy path in this class.
 */
class Rule009Rule008Rule033Rule026DebitRoutingTest {

    @Test
    @DisplayName("RULE-009 (P0) first slice: type 3 happy path -> begin, savepoint, sp_ndc_ahcc, movement 'P', stubs, commit; return 0 / o_error 0")
    void rule009_currentAccountHappyPathSequenceAndCommittedState() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50").debitNote(0, 9001);
        DebitRequest request = Requests.currentAccount().tipctaEmp(3).build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "notificationStep",
                "sp_grb_mov_y_frmpgo",
                "commissionStep",
                "orderHeaderStep",
                "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo");
        assertThat(ase.transactionOpen()).isFalse();
        MovementCommand movement = ase.onlyMovement();
        assertThat(movement.iEstProceso()).isEqualTo("P");
        assertThat(movement.iCodError()).isEqualTo(0);
        assertThat(movement.iTranNcnd()).isEqualTo(9001);
    }

    @Test
    @DisplayName("RULE-009: sp_ndc_ahcc receives every legacy argument for a type 3 debit (lines 676-730)")
    void rule009_debitNoteArgumentsForCurrentAccount() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount()
                .sSsn(77).sSrv("SRVTEST").sUser("usrtest").sTerm("TERM01").sOfi(5)
                .tipctaEmp(3).numctaEmp(Requests.ACCOUNT).valorDebito("100.00").monDebito(1)
                .tarjeta("4000XXXXXXXX0001").servicio("ROLPAGO").aplcobis("N").refProv("PROV-REF-01")
                .comision("1.00").valorComision("2.50").canal("DIR").frmPagcob("CUE").frmPagcobSpi(null)
                .orden(123456).ordenEmpresa(4587).build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote()).isEqualTo(new DebitNoteCommand(
                77, "SRVTEST", "usrtest", "TERM01", 0, Requests.PROCESS_DATE, 2701,
                Requests.ACCOUNT, 3, "0150", new BigDecimal("100.00"), 1,
                "4000XXXXXXXX0001", "ROLPAGO", "4587", "N", "PROV-REF-01",
                new BigDecimal("1.00"), "DIR", null, 123456, "CUE", 123456, 0, 5, new BigDecimal("0.50")));
    }

    @Test
    @DisplayName("RULE-009: @s_ssn null -> 0 on sp_ndc_ahcc (line 266)")
    void rule009_nullSessionSequenceBecomesZero() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().sSsn(null).build());

        assertThat(ase.onlyDebitNote().sSsn()).isEqualTo(0);
    }

    @Test
    @DisplayName("RULE-009: type 4 (savings) also goes through sp_ndc_ahcc with tipo_cuenta 4")
    void rule009_savingsAccountUsesDebitNote() {
        FakeAseSession ase = new FakeAseSession();

        DebitResult result = ase.service().debit(Requests.currentAccount().tipctaEmp(4).build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyDebitNote().iTipoCuenta()).isEqualTo(4);
        assertThat(ase.virtualDebitNotes()).isEmpty();
        assertThat(ase.ledgerDebits()).isEmpty();
    }

    @Test
    @DisplayName("RULE-009 (P0) + RULE-033: type 12 -> sp_vi_ndc_automatica with ofi 0, canal 'SAT', verf 'S', batch 1, order as alt; no commission fields (lines 628-662)")
    void rule009_rule033_virtualAccountUsesVirtualDebitNoteWithSatChannel() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50").virtualDebit(0, 0, 9002);
        DebitRequest request = Requests.currentAccount().tipctaEmp(12).sSrv("SRVTEST").sUser("usrtest").sTerm("TERM01")
                .sOfi(5).canal("BNK").aplcobis("N").valorDebito("100.00").monDebito(1).empresa(500)
                .comision("2.50").valorComision("0").orden(123456).ordenEmpresa(4587).build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyVirtualDebitNote()).isEqualTo(new VirtualDebitNoteCommand(
                "SRVTEST", 0, "usrtest", "TERM01", 2701, Requests.ACCOUNT, new BigDecimal("100.00"), "0150", 1, 500,
                "SAT", "S", Boolean.TRUE, "4587", 123456));
        assertThat(ase.debitNotes()).isEmpty();
        assertThat(ase.ledgerDebits()).isEmpty();
        assertThat(ase.onlyMovement().iTranNcnd()).isEqualTo(9002);
        // the movement keeps the request channel: only the virtual debit note is forced to SAT
        assertThat(ase.onlyMovement().iCanal()).isEqualTo("BNK");
        assertThat(ase.committedProcedures()).containsExactly("sp_vi_ndc_automatica", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-009: virtual debit error code is the RETURN value, not the @o_error output (line 666)")
    void rule009_virtualDebitReturnValueWinsOverOutputError() {
        FakeAseSession ase = new FakeAseSession().virtualDebit(0, 555, 9002);

        DebitResult result = ase.service().debit(Requests.currentAccount().tipctaEmp(12).build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
    }

    @Test
    @DisplayName("RULE-009: virtual debit return 201045 -> exit A with 201045")
    void rule009_virtualDebitFailureIsExitA() {
        FakeAseSession ase = new FakeAseSession().virtualDebit(201045, 0, null);

        DebitResult result = ase.service().debit(Requests.currentAccount().tipctaEmp(12).build());

        assertThat(result).isEqualTo(new DebitResult(201045, 201045, null));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(201045);
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-026: aplcobis 'S' -> batch 0 (false) on the virtual debit note (lines 176-178)")
    void rule026_cobisOnlineIsNotBatch() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().tipctaEmp(12).aplcobis("S").build());

        assertThat(ase.onlyVirtualDebitNote().iBatch()).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("RULE-026: aplcobis 'N' -> batch 1 (true)")
    void rule026_nonCobisIsBatch() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().tipctaEmp(12).aplcobis("N").build());

        assertThat(ase.onlyVirtualDebitNote().iBatch()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("RULE-026 edge: aplcobis 'X' (neither S nor N) -> batch NULL")
    void rule026_unexpectedAplcobisLeavesBatchNull() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().tipctaEmp(12).aplcobis("X").build());

        assertThat(ase.onlyVirtualDebitNote().iBatch()).isNull();
    }

    @ParameterizedTest(name = "RULE-008 (P0): account type {0} -> 122001, no debit port, movement X committed")
    @ValueSource(ints = {5, 7, 8, 0, -1, 13})
    void rule008_unsupportedAccountTypeIs122001WithMovementX(int accountType) {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().tipctaEmp(accountType).aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(122001, 122001, null));
        assertThat(ase.debitNotes()).isEmpty();
        assertThat(ase.virtualDebitNotes()).isEmpty();
        assertThat(ase.ledgerDebits()).isEmpty();
        MovementCommand movement = ase.onlyMovement();
        assertThat(movement.iEstProceso()).isEqualTo("X");
        assertThat(movement.iCodError()).isEqualTo(122001);
        assertThat(movement.iTipoCta()).isEqualTo(accountType);
        assertThat(movement.iTranNcnd()).as("no debit note ran, sequence stays NULL").isNull();
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision", "sp_con_confcontable", "begin", "savepoint:sp_debito_empresa",
                "rollbackToSavepoint:sp_debito_empresa", "sp_grb_mov_y_frmpgo", "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.errorReports()).as("exit A never calls sp_cerror").isEmpty();
    }

    @Test
    @DisplayName("RULE-008: null account type is also 122001 (NULL is not IN (3,4,12) nor IN (9))")
    void rule008_nullAccountTypeIs122001() {
        FakeAseSession ase = new FakeAseSession();

        DebitResult result = ase.service().debit(Requests.currentAccount().tipctaEmp(null).build());

        assertThat(result).isEqualTo(new DebitResult(122001, 122001, null));
        assertThat(ase.onlyMovement().iTipoCta()).isNull();
    }

    @Test
    @DisplayName("RULE-008: with aplcobis 'S' the 122001 exit A still returns the code in both places and never reports to COBIS")
    void rule008_unsupportedTypeFromCobisSameExitA() {
        FakeAseSession ase = new FakeAseSession();

        DebitResult result = ase.service().debit(Requests.currentAccount().tipctaEmp(7).aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(122001, 122001, null));
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-033: type 3 with bundled commission and card passes tcomision, nchq, solca, card and provider detail to sp_ndc_ahcc")
    void rule033_currentAccountCarriesCommissionAndCardData() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().tipctaEmp(3).comision("2.50").valorComision("1.00")
                .tarjeta("4000XXXXXXXX0001").refProv("PROV-REF-01").build();

        ase.service().debit(request);

        DebitNoteCommand note = ase.onlyDebitNote();
        assertThat(note.iTcomision()).isEqualByComparingTo("2.50");
        assertThat(note.iNchq()).isEqualTo(2); // 1.00 / 0.50: the separate commission is the numerator
        assertThat(note.iSolca()).isEqualByComparingTo("0.50");
        assertThat(note.iTarjeta()).isEqualTo("4000XXXXXXXX0001");
        assertThat(note.iDetalle()).isEqualTo("PROV-REF-01");
        assertThat(note.iSecuencial()).isEqualTo(0);
        assertThat(note.iOrdenBanco()).isEqualTo(Requests.BANK_ORDER);
        assertThat(note.iAlt()).isEqualTo(Requests.BANK_ORDER);
    }
}
