package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B7 (lines 746-1256) is OUT of Phase 1 scope: the notification step is a stub that answers
 * "not configured" (no ad_servicios_sms mapping, line 814 false). These tests prove the Phase 1
 * exit criterion: the stub leaves @w_cod_errord, the debit value and the exits untouched, and it is
 * invoked exactly where the legacy enters the block (debit returned 0, account type 3/4/12,
 * service not PAGOPRV). Related rule ids for the tracer: RULE-034 (Phase 4), RULE-012 (line 1248
 * overwrite is not reached when not configured).
 */
class NotificationStubTest {

    @Test
    @DisplayName("RULE-012/RULE-034 stub: 'not configured' leaves the debit error code 0 and the flow commits with 'P'")
    void notConfiguredLeavesDebitErrorCodeUntouched() {
        FakeAseSession ase = new FakeAseSession().notification(NotificationOutcome.notConfigured());

        DebitResult result = ase.service().debit(Requests.currentAccount().valorDebito("100.00").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(0);
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
        assertThat(ase.calls()).containsSubsequence("sp_ndc_ahcc", "notificationStep", "sp_grb_mov_y_frmpgo", "commit");
    }

    @Test
    @DisplayName("RULE-034 stub: the step receives the working values (service, trn, causal, debit value, commissions, sequence)")
    void stubReceivesWorkingValues() {
        FakeAseSession ase = new FakeAseSession().debitNote(0, 9001);
        DebitRequest request = Requests.currentAccount().servicio("TRANSCLI").valorDebito("100.50").comision("0.50")
                .valorComision("0").build();

        ase.service().debit(request);

        NotificationContext ctx = ase.onlyNotification();
        assertThat(ctx.request()).isSameAs(request);
        assertThat(ctx.servicio()).isEqualTo("TRANSCLI");
        assertThat(ctx.trn()).isEqualTo(2701);
        assertThat(ctx.causal()).isEqualTo("0150");
        assertThat(ctx.valorDebito()).isEqualByComparingTo("100.50");
        assertThat(ctx.comision()).as("after RULE-027 normalization").isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ctx.valorComision()).isEqualByComparingTo("0.50");
        assertThat(ctx.tranNcnd()).isEqualTo(9001);
    }

    @Test
    @DisplayName("Stub is reached for account types 3, 4 and 12 on success")
    void stubReachedForDebitNoteAccountTypes() {
        for (int type : new int[] {3, 4, 12}) {
            FakeAseSession ase = new FakeAseSession();
            ase.service().debit(Requests.currentAccount().tipctaEmp(type).build());
            assertThat(ase.notifications()).as("type %s", type).hasSize(1);
        }
    }

    @Test
    @DisplayName("Stub is NOT reached when the debit failed (line 746: @w_return = 0)")
    void stubNotReachedOnDebitFailure() {
        FakeAseSession ase = new FakeAseSession().debitNote(201045, null);

        ase.service().debit(Requests.currentAccount().build());

        assertThat(ase.notifications()).isEmpty();
    }

    @Test
    @DisplayName("Stub is NOT reached for PAGOPRV (line 746), even padded ' PAGOPRV '")
    void stubNotReachedForSupplierPayments() {
        FakeAseSession ase = new FakeAseSession();
        FakeAseSession padded = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("PAGOPRV").build());
        padded.service().debit(Requests.currentAccount().servicio(" PAGOPRV ").build());

        assertThat(ase.notifications()).isEmpty();
        assertThat(padded.notifications()).isEmpty();
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
    }

    @Test
    @DisplayName("Stub is NOT reached for ledger accounts (type 9) nor for rejected types")
    void stubNotReachedOutsideDebitNoteBranch() {
        FakeAseSession ledger = new FakeAseSession();
        FakeAseSession rejected = new FakeAseSession();

        ledger.service().debit(Requests.currentAccount().tipctaEmp(9).build());
        rejected.service().debit(Requests.currentAccount().tipctaEmp(7).build());

        assertThat(ledger.notifications()).isEmpty();
        assertThat(rejected.notifications()).isEmpty();
    }

    @Test
    @DisplayName("RULE-012 (line 1248, preserved for Phase 4): a CONFIGURED notification returning 777 turns the debit into exit A with 777")
    void configuredNotificationFailureOverwritesDebitErrorCode() {
        FakeAseSession ase = new FakeAseSession().notification(new NotificationOutcome(true, 777, null));

        DebitResult result = ase.service().debit(Requests.currentAccount().build());

        assertThat(result).isEqualTo(new DebitResult(777, 777, null));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(777);
        assertThat(ase.calls()).containsSubsequence("notificationStep", "rollbackToSavepoint:sp_debito_empresa",
                "sp_grb_mov_y_frmpgo", "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-003 hook (Phase 4): a configured notification may replace the working debit value used by the movement")
    void configuredNotificationMayReplaceDebitValue() {
        FakeAseSession ase = new FakeAseSession()
                .notification(new NotificationOutcome(true, 0, new BigDecimal("100.00")));

        ase.service().debit(Requests.currentAccount().valorDebito("100.50").build());

        assertThat(ase.onlyDebitNote().iValor()).as("the debit note already went out with 100.50").isEqualByComparingTo("100.50");
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
    }
}
