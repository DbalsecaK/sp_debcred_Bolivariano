package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Phase 4 exit criterion "the stub from Phase 1 is replaced": the production wiring
 * ({@link DebitFlowConfiguration}) builds the real {@link CustomerNotifications} over the session, so a
 * configured notification reaches {@code sp_eventos} and a failing one reverses the debit (RULE-034,
 * RULE-012). Uses the production {@code DebitFlow}, not {@code FakeAseSession.service()}. Fake data only.
 */
class Rule034NotificationWiringTest {

    @Test
    @DisplayName("RULE-034: the production DebitFlow notifies through sp_eventos when the SMS service is configured")
    void rule034_productionWiringNotifies() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").currentOwner(Requests.ACCOUNT, 700001);

        DebitResult result = new DebitFlowConfiguration().debitFlow().over(ase)
                .debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyEvent().iServicio()).isEqualTo("ROL");
        assertThat(ase.notifications()).as("the fake's scripted/logging step is NOT used by the production wiring").isEmpty();
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_eventos", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-034 + RULE-012: the production DebitFlow reverses the debit when sp_eventos fails")
    void rule034_rule012_productionWiringReversesOnFailure() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").event(30001);

        DebitResult result = new DebitFlowConfiguration().debitFlow().over(ase)
                .debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(30001, 30001, null));
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-035: the production DebitFlow with an empty catalogue behaves as the Phase 1 stub (nothing else read, commit)")
    void rule035_productionWiringUnconfigured() {
        FakeAseSession ase = new FakeAseSession();

        DebitResult result = new DebitFlowConfiguration().debitFlow().over(ase).debit(Requests.currentAccount().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms");
    }
}
