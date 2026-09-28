package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.BlockQuery;
import com.nexti.debcred.support.Requests;

/**
 * The 'OTRO' path (sp_debcred_empresa.sp:1094-1242):
 * <ul>
 *   <li>RULE-005 (1102-1154): debtor product and client: 3 -> 'CTE' + {@code cc_cliente}; 4 -> 'AHO' +
 *       {@code ah_cliente}; 12 -> 'VIR' + {@code vi_cliente}, 'AHO' when {@code vi_prod_banc = 13}; no master row
 *       keeps the product and a NULL client.</li>
 *   <li>RULE-021 (1162-1208): the event is suppressed by an active {@code ba_bloqueaNotificacionSAT} row
 *       named {@code rtrim(service) + '-' + rtrim(sms service)} whose other field is {@code rtrim(@i_sp_name)},
 *       or for channel 'BCE' + service 'ROLPAGO' + NULL debit account. The owner read happens BEFORE the
 *       block-list read, and both run even when the event is then suppressed.</li>
 *   <li>RULE-034 (1210-1242): {@code cob_internet..sp_eventos} with exactly the legacy arguments; its return
 *       becomes {@code @w_cod_errord} (1248). Suppressed: {@code @w_return} stays 0.</li>
 * </ul>
 * Fake data only (client ids 7000xx, account 0000012345).
 */
class Rule005Rule021EventNotificationTest {

    private static final String ACCOUNT = Requests.ACCOUNT;

    private static FakeAseSession rolpago() {
        return new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").smsService("ROL-BCE", "NOTIFICA ROLPAGO");
    }

    // ---- sp_eventos arguments (1210-1242) -------------------------------------------------------

    @Test
    @DisplayName("RULE-034 + RULE-005: current account, DIR -> sp_eventos('I', 'SAT', 'ROL', 3, account, '100.00', account, 'CTE', NULL, NULL, client, '0.00', NULL, 'SAT')")
    void rule034_rule005_eventArgumentsCurrentAccount() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 700001);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal("DIR")
                .tipctaEmp(3).valorDebito("100.00").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyEvent()).isEqualTo(new EventCommand("I", "SAT", "ROL", 3, ACCOUNT, "100.00", ACCOUNT, "CTE",
                null, null, 700001, "0.00", null, "SAT"));
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms", "ad_notificacion_basica", "cc_ctacte",
                "ba_bloqueaNotificacionSAT", "sp_eventos");
        assertThat(ase.ownerQueries()).containsExactly("cc_ctacte:" + ACCOUNT);
    }

    @Test
    @DisplayName("RULE-005: savings account (4) -> 'AHO' and ah_cliente")
    void rule005_savingsAccount() {
        FakeAseSession ase = rolpago().savingsOwner(ACCOUNT, 700004);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").tipctaEmp(4).build());

        assertThat(ase.onlyEvent().iProdDeb()).isEqualTo("AHO");
        assertThat(ase.onlyEvent().iCliente()).isEqualTo(700004);
        assertThat(ase.onlyEvent().iProducto()).isEqualTo(4);
        assertThat(ase.ownerQueries()).containsExactly("ah_cuenta:" + ACCOUNT);
    }

    @ParameterizedTest(name = "RULE-005: virtual account (12), vi_prod_banc {0} -> product {1}, client {2}")
    @CsvSource(value = {"13, AHO, 700012", "7, VIR, 700012", "NULL, VIR, 700012"}, nullValues = "NULL")
    void rule005_virtualAccount(Integer prodBanc, String prodDeb, Integer cliente) {
        FakeAseSession ase = rolpago().virtualOwner(ACCOUNT, 700012, prodBanc);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").tipctaEmp(12).build());

        assertThat(ase.onlyEvent().iProdDeb()).isEqualTo(prodDeb);
        assertThat(ase.onlyEvent().iCliente()).isEqualTo(cliente);
        assertThat(ase.onlyEvent().iProducto()).isEqualTo(12);
        assertThat(ase.ownerQueries()).containsExactly("vi_cuenta:" + ACCOUNT);
    }

    @ParameterizedTest(name = "RULE-005: account type {0} with no master row -> product {1}, client NULL, event still sent")
    @CsvSource({"3, CTE", "4, AHO", "12, VIR"})
    void rule005_noMasterRow(int type, String prodDeb) {
        FakeAseSession ase = rolpago();

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").tipctaEmp(type).build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyEvent().iProdDeb()).isEqualTo(prodDeb);
        assertThat(ase.onlyEvent().iCliente()).isNull();
    }

    @Test
    @DisplayName("RULE-005: a master row with a NULL client gives a NULL @i_cliente")
    void rule005_nullClient() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, null);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(ase.onlyEvent().iCliente()).isNull();
        assertThat(ase.onlyEvent().iProdDeb()).isEqualTo("CTE");
    }

    // ---- block list (1162-1180) ------------------------------------------------------------------

    @Test
    @DisplayName("RULE-021 (brief example): 'ROLPAGO-ROL' blocked for the calling procedure -> no event, code 0, commit; owner read and block read both ran, in that order")
    void rule021_blockListSuppressesTheEvent() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 700001).blockedNotification("ROLPAGO-ROL", "sp_x_fake");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").spName("sp_x_fake").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.events()).isEmpty();
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms", "ad_notificacion_basica", "cc_ctacte",
                "ba_bloqueaNotificacionSAT");
        assertThat(ase.onlyBlockQuery()).isEqualTo(new BlockQuery("ROLPAGO", "ROL", "sp_x_fake"));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(0);
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-021: the block list is keyed by the calling procedure: another caller still notifies")
    void rule021_blockListIsPerCaller() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 700001).blockedNotification("ROLPAGO-ROL", "sp_x_fake");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").spName("sp_y_fake").build());

        assertThat(ase.events()).hasSize(1);
    }

    @Test
    @DisplayName("RULE-021: the block read receives the RAW service and caller; rtrim is applied by the read ('ROLPAGO  ' is still blocked)")
    void rule021_blockReadArgumentsAreRaw() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 700001).blockedNotification("ROLPAGO-ROL", "sp_x_fake");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO  ").spName("sp_x_fake  ").build());

        assertThat(ase.onlyBlockQuery()).isEqualTo(new BlockQuery("ROLPAGO  ", "ROL", "sp_x_fake  "));
        assertThat(ase.events()).isEmpty();
    }

    @Test
    @DisplayName("RULE-021: an inactive block row does not suppress")
    void rule021_inactiveBlockRow() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 1)
                .catalogRow("ba_bloqueaNotificacionSAT", "BLQ9", "ROLPAGO-ROL", "sp_x_fake", "I");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").spName("sp_x_fake").build());

        assertThat(ase.events()).hasSize(1);
    }

    // ---- BCE exception (1186-1208) ----------------------------------------------------------------

    @Test
    @DisplayName("RULE-021: channel BCE + ROLPAGO + NULL debit account -> no event, code 0; the owner and block reads still ran")
    void rule021_bcePayrollWithoutAccount() {
        FakeAseSession ase = rolpago();

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal("BCE").numctaEmp(null).build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.events()).isEmpty();
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms", "ad_notificacion_basica", "cc_ctacte",
                "ba_bloqueaNotificacionSAT");
        assertThat(ase.ownerQueries()).containsExactly("cc_ctacte:null");
    }

    @Test
    @DisplayName("RULE-021 + RULE-035: channel BCE + ROLPAGO WITH an account -> event on channel 'BCE' with a NULL description")
    void rule021_bcePayrollWithAccountNotifies() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 1);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal("BCE").build());

        assertThat(ase.onlyEvent().iCanal()).isEqualTo("BCE");
        assertThat(ase.onlyEvent().iDescCanal()).isNull();
    }

    @Test
    @DisplayName("RULE-021: the BCE exception needs service = 'ROLPAGO' (Sybase '=', a leading blank does not match) and channel BCE")
    void rule021_bceExceptionIsNarrow() {
        FakeAseSession otherService = new FakeAseSession().smsService("SVB-BCE", "NOTIFICA SERVBAS");
        FakeAseSession leadingBlank = rolpago();
        FakeAseSession otherChannel = rolpago();

        otherService.service().debit(Requests.currentAccount().servicio("SERVBAS").canal("BCE").numctaEmp(null).build());
        leadingBlank.service().debit(Requests.currentAccount().servicio(" ROLPAGO").canal("BCE").numctaEmp(null).build());
        otherChannel.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal("DIR").numctaEmp(null).build());

        assertThat(otherService.events()).hasSize(1);
        assertThat(leadingBlank.events()).hasSize(1);
        assertThat(otherChannel.events()).hasSize(1);
        assertThat(otherChannel.onlyEvent().iCuenta()).isNull();
        assertThat(otherChannel.onlyEvent().iCtaDeb()).isNull();
    }

    // ---- the event's return value (1248) ---------------------------------------------------------

    @Test
    @DisplayName("RULE-034 + RULE-012: sp_eventos returns 30001 -> exit A (30001, 30001)")
    void rule034_rule012_eventFailureIsExitA() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 1).event(30001);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(30001, 30001, null));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
    }

    @Test
    @DisplayName("RULE-021 + RULE-034: a failing sp_eventos is irrelevant when the event is blocked (never called)")
    void rule021_blockedNeverCallsTheFailingNotifier() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 1).event(30001).blockedNotification("ROLPAGO-ROL", "sp_x_fake");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").spName("sp_x_fake").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.calls()).doesNotContain("sp_eventos");
    }

    @Test
    @DisplayName("RULE-005: the 'OTRO' step outcome has @o_error 0 even though sp_eventos has no @o_error")
    void rule005_otroOutcome() {
        FakeAseSession ase = rolpago().currentOwner(ACCOUNT, 1).event(0);
        DebitRequest request = Requests.currentAccount().servicio("ROLPAGO").build();

        NotificationOutcome outcome = new CustomerNotifications(ase, ase, ase, ase, ase).notify(new NotificationContext(
                request, request.iServicio(), 2701, "0150", request.iValorDebito(), java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, 9001));

        assertThat(outcome).isEqualTo(new NotificationOutcome(true, 0, null, 0, null));
    }
}
