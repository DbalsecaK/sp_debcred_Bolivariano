package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.DetailTable;
import com.nexti.debcred.support.FakeAseSession.HeaderRow;
import com.nexti.debcred.support.FakeAseSession.HeaderTable;
import com.nexti.debcred.support.FakeAseSession.HeaderUpdate;
import com.nexti.debcred.support.FakeAseSession.SwiftQuery;
import com.nexti.debcred.support.Requests;

/**
 * Brief section 4, walkthroughs 1-4 end to end with the REAL notification step and a configured
 * notification (the Phase 4 exit criterion). Walkthrough 1 with the step stubbed stays in
 * {@link Walkthrough1EndToEndTest}. Every test asserts the full call sequence and the committed state.
 * Fake data only.
 */
class Walkthroughs1To4WithNotificationsTest {

    private static final int ORDER = Requests.BANK_ORDER;
    private static final String GROUP = "000000017";

    @Test
    @DisplayName("Walkthrough 1 (RULE-009, RULE-012, RULE-013, RULE-014, RULE-034, RULE-004, RULE-003): TRANSCLI debit, sp_eventos, movement with the ordered value, commission, header I -> T, commit")
    void walkthrough1_rule034_rule004_rule003_transcliWithNotification() {
        FakeAseSession ase = new FakeAseSession()
                .smsService("TRC-SAT", "NOTIFICA TRANSCLI")
                .bceInstitution(GROUP + "BANCO DESTINO FAKE")
                .interbankDetail(DetailTable.LIVE, ORDER, GROUP, 4, "2200334455")
                .currentOwner(Requests.ACCOUNT, 700001)
                .headerRow(ORDER, "CUE", "TRANSCLI", "I")
                .headerRow(ORDER, "EFE", "TRANSCLI", null)
                .headerRow(ORDER, "CHE", "TRANSCLI", "I");
        DebitRequest request = Requests.currentAccount().servicio("TRANSCLI").tipctaEmp(3).canal("DIR").opcion("02")
                .frmPagcob("CUE").comision("0").valorComision("1.50").valorDebito("250.00").valorOrdenado("250.00")
                .aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "notificationStep",
                "sp_eventos",
                "sp_grb_mov_y_frmpgo",
                "commissionStep",
                "sp_grb_comision",
                "orderHeaderStep",
                "commit");
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms", "bp_detalle:ad_cuentas_bce",
                "ad_notificacion_basica", "cc_ctacte", "ba_bloqueaNotificacionSAT", "sp_eventos");
        assertThat(ase.onlyEvent()).isEqualTo(new EventCommand("I", "SAT", "TRC", 3, Requests.ACCOUNT, "250.00",
                Requests.ACCOUNT, "CTE", "2200334455", "AHO", 700001, "0.00", "BANCO DESTINO FAKE", "SAT"));
        assertThat(ase.headerUpdates()).containsExactly(
                new HeaderUpdate(HeaderTable.LIVE, ORDER, List.of("CUE", "EFE", "CHL"), "TRANSCLI", 0, 2));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).as("@wRowdbBiz from the live interbank read").isEqualTo(1);

        // committed state
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_eventos", "sp_grb_mov_y_frmpgo", "sp_grb_comision");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("250.00");
        assertThat(ase.headerRows(HeaderTable.LIVE)).containsExactly(
                new HeaderRow(ORDER, "CUE", "TRANSCLI", "T", 0),
                new HeaderRow(ORDER, "EFE", "TRANSCLI", "T", 0),
                new HeaderRow(ORDER, "CHE", "TRANSCLI", "I", null));
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.rollbacks()).isZero();
        assertThat(ase.transactionOpen()).isFalse();
    }

    @Test
    @DisplayName("Walkthrough 2 (RULE-038, RULE-019, RULE-006, RULE-013, RULE-016, RULE-010): TRANSWIFT, SWIFT reference, archived detail read from history, cost 17.00, two commissions, exempt from 122004, commit")
    void walkthrough2_rule019_rule006_rule016_transwiftWithNotification() {
        FakeAseSession ase = new FakeAseSession()
                .smsService("TSW-SAT", "NOTIFICA TRANSWIFT")
                .swiftDetail(DetailTable.HISTORY, ORDER, 2, Requests.COMPANY, "GRPEXT001", "CUENTA BENEFICIARIO EXTERIOR FAKE 0001")
                .currentOwner(Requests.ACCOUNT, 700001)
                .noHeaderRows();
        DebitRequest request = Requests.currentAccount().servicio("TRANSWIFT").codSwift("BICFAKEX").secuencial(2)
                .canal("DIR").opcion("02").comision("0").valorComision("12.00").valor2Swift("5.00")
                .valorDebito("1000.00").valorOrdenado("1000.00").aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "notificationStep",
                "sp_eventos",
                "sp_grb_mov_y_frmpgo",
                "commissionStep",
                "sp_grb_comision",
                "sp_grb_comision",
                "orderHeaderStep",
                "commit");
        assertThat(ase.onlyDebitNote().iRef()).isEqualTo("COD:BICFAKEX");
        assertThat(ase.swiftQueries()).containsExactly(
                new SwiftQuery(DetailTable.LIVE, ORDER, 2, Requests.COMPANY),
                new SwiftQuery(DetailTable.HISTORY, ORDER, 2, Requests.COMPANY));
        assertThat(ase.onlyEvent()).isEqualTo(new EventCommand("I", "SAT", "TSW", 3, Requests.ACCOUNT, "1000.00",
                Requests.ACCOUNT, "CTE", "CUENTA BENEFICIARIO EXTERIOR F", null, 700001, "17.00", "GRPEXT001", "SAT"));
        assertThat(ase.commissionCommands()).extracting(CommissionCommand::iTipoafec).containsExactly(null, "16");
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::rowCount).containsExactly(0, 0);
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_eventos", "sp_grb_mov_y_frmpgo",
                "sp_grb_comision", "sp_grb_comision");
        assertThat(ase.onlyMovement().iValorMov()).as("TRANSWIFT keeps the debit value").isEqualByComparingTo("1000.00");
        assertThat(ase.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("Walkthrough 3 (RULE-012, RULE-015, RULE-034): the debit fails -> exit A; the configured notification is never read")
    void walkthrough3_rule012_rule034_debitFailureSkipsNotification() {
        FakeAseSession ase = new FakeAseSession()
                .smsService("TRC-SAT", "NOTIFICA TRANSCLI")
                .bceInstitution(GROUP + "BANCO DESTINO FAKE")
                .interbankDetail(DetailTable.LIVE, ORDER, GROUP, 4, "2200334455")
                .currentOwner(Requests.ACCOUNT, 700001)
                .headerRow(ORDER, "CUE", "TRANSCLI", "I")
                .debitNote(201045, null);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("TRANSCLI").valorDebito("100.50")
                .valorOrdenado("100.00").comision("0").valorComision("1.50").build());

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
        assertThat(ase.notificationLog()).isEmpty();
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.onlyMovement().iValorMov()).as("no override without B7").isEqualByComparingTo("100.50");
        assertThat(ase.headerState(ORDER, "CUE", "TRANSCLI")).isEqualTo("I");
    }

    @Test
    @DisplayName("Walkthrough 4 (RULE-013, RULE-034): the commission fails after debit and notification -> exit B: everything rolled back (event included), 'X' movement in autocommit, order stays I")
    void walkthrough4_rule013_rule034_commissionFailureDiscardsTheNotification() {
        FakeAseSession ase = new FakeAseSession()
                .smsService("ROL-SAT", "NOTIFICA ROLPAGO")
                .currentOwner(Requests.ACCOUNT, 700001)
                .headerRow(ORDER, "CUE", "ROLPAGO", "I")
                .commission(0, 122010);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").comision("1.50")
                .valorComision("2.00").aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "notificationStep",
                "sp_eventos",
                "sp_grb_mov_y_frmpgo",
                "commissionStep",
                "sp_grb_comision",
                "rollback",
                "sp_grb_mov_y_frmpgo");
        assertThat(ase.committedProcedures()).as("only the exit-B 'X' movement survives").containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.lastMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.headerState(ORDER, "CUE", "ROLPAGO")).as("re-runnable").isEqualTo("I");
        assertThat(ase.commits()).isZero();
    }
}
