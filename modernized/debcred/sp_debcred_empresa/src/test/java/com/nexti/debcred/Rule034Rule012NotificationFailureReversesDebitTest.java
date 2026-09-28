package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Brief Phase 4 risk (1), RULE-034 + RULE-012 (P0), confirmed as parity by the approver: a notification
 * failure reverses the debit. Line 1248 {@code select @w_cod_errord = @w_return} overwrites the debit's 0
 * with the notifier's return value, so B9 takes exit A: {@code rollback tran sp_debito_empresa} (1338)
 * removes the debit AND the notification written after the savepoint, the movement is written with status
 * 'X' and that code, the transaction is committed, and the code is returned in BOTH channels (no
 * sp_cerror). These tests assert the committed state, not only the calls. Fake data only.
 */
class Rule034Rule012NotificationFailureReversesDebitTest {

    private static FakeAseSession eventFails(int code) {
        return new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").currentOwner(Requests.ACCOUNT, 700001)
                .event(code);
    }

    @ParameterizedTest(name = "RULE-034 + RULE-012 (P0): sp_eventos returns 30001, aplcobis ''{0}'' -> exit A (30001, 30001), only the 'X' movement survives")
    @ValueSource(strings = {"N", "S"})
    void rule034_rule012_eventFailureRollsBackToTheSavepoint(String aplcobis) {
        FakeAseSession ase = eventFails(30001);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").aplcobis(aplcobis)
                .comision("0").valorComision("2.00").build());

        assertThat(result).isEqualTo(new DebitResult(30001, 30001, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "notificationStep",
                "sp_eventos",
                "rollbackToSavepoint:sp_debito_empresa",
                "sp_grb_mov_y_frmpgo",
                "commit");
        // committed state: the debit note and the event are gone, the failure movement is kept
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(30001);
        assertThat(ase.commissionSteps()).as("B10 not reached: the commission is not charged").isEmpty();
        assertThat(ase.orderHeaderSteps()).as("B12 not reached").isEmpty();
        assertThat(ase.errorReports()).as("exit A never calls sp_cerror").isEmpty();
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.rollbacks()).as("no full rollback").isZero();
        assertThat(ase.transactionOpen()).isFalse();
    }

    @Test
    @DisplayName("RULE-034 + RULE-012: virtual account (12): the sp_vi_ndc_automatica write is rolled back to the savepoint too")
    void rule034_rule012_virtualAccountDebitIsReversed() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").virtualOwner(Requests.ACCOUNT, 1, 7)
                .event(30002);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").tipctaEmp(12).build());

        assertThat(result).isEqualTo(new DebitResult(30002, 30002, null));
        assertThat(ase.calls()).containsSubsequence("sp_vi_ndc_automatica", "notificationStep", "sp_eventos",
                "rollbackToSavepoint:sp_debito_empresa", "sp_grb_mov_y_frmpgo", "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-034: sp_eventos returns 0 -> the debit, the event and the 'P' movement are all committed")
    void rule034_eventSuccessCommitsEverything() {
        FakeAseSession ase = eventFails(0);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_eventos", "sp_grb_mov_y_frmpgo");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
    }

    @Test
    @DisplayName("RULE-034 + RULE-021: no notifier called (blocked) -> @w_return stays 0: nothing is reversed")
    void rule034_rule021_blockedLeavesTheDebit() {
        FakeAseSession ase = eventFails(30001).blockedNotification("ROLPAGO-ROL", "sp_test_caller");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-034 + RULE-012: pa_sat_pnotificacion returns 40002 -> exit A, the notification write is rolled back with the debit")
    void rule034_rule012_basicNotifierFailureRollsBackToTheSavepoint() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", "B")
                .basicNotification(40002, 40002, "FALLO FAKE");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(40002, 40002, null));
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.errorReports()).isEmpty();
    }
}
