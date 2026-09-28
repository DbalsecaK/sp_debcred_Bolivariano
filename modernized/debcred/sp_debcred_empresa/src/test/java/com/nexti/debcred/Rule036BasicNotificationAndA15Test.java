package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.DetailTable;
import com.nexti.debcred.support.FakeAseSession.OrderQuery;
import com.nexti.debcred.support.Requests;

/**
 * RULE-036 (sp_debcred_empresa.sp:994-1094): the classification {@code ct_otro_campo_catalogo} of
 * {@code ad_notificacion_basica} (exact service code, table and row active) is assigned to
 * {@code @w_serv_pago_dir varchar(4)} and defaults to 'OTRO'. 'B' reads the beneficiary name and group
 * reference from the UNQUALIFIED {@code bp_detalle} by order (brief 7 A8: {@code cobis}), falling back to
 * {@code db_sat_his..bp_detalle_his} when {@code @@rowcount = 0}, and calls {@code pa_sat_pnotificacion}
 * with exactly the arguments of lines 1052-1082. 'OTRO' goes to {@code sp_eventos}
 * ({@link Rule005Rule021EventNotificationTest}); any other value sends nothing.
 *
 * <p>Brief section 7 A15 (approved as parity): {@code pa_sat_pnotificacion} writes the procedure's own
 * {@code @o_error} (line 1080). The success exit returns {@code isnull(@o_error, 0)} (2134), so a successful
 * debit can answer {@code (0, <notifier o_error>)}. Exit C in COBIS mode (2150-2158) does NOT assign
 * {@code @o_error} either, so it also shows the notifier's value, NULL included; exit C in the other mode,
 * exit A and exit B overwrite it. Fake data only.
 */
class Rule036BasicNotificationAndA15Test {

    private static final int ORDER = Requests.BANK_ORDER;

    /** ROLPAGO classified 'B' (RULE-036 example), SMS row on 'SAT', one live beneficiary row. */
    private static FakeAseSession basic() {
        return new FakeAseSession()
                .smsService("ROL-SAT", "NOTIFICA ROLPAGO")
                .smsService("ROL-IBK", "NOTIFICA ROLPAGO")
                .notificationClassRow("ROLPAGO", "B")
                .beneficiaryDetail(DetailTable.LIVE, ORDER, "BENEFICIARIO FAKE UNO", "GRP000001")
                .currentOwner(Requests.ACCOUNT, 700001);
    }

    private static Requests rolpago() {
        return Requests.currentAccount().servicio("ROLPAGO").canal("DIR").valorDebito("251.20").valorOrdenado("250.00")
                .comision("0").valorComision("1.20").secuencial(3);
    }

    // ---- the call (1052-1082) ------------------------------------------------------------------

    @Test
    @DisplayName("RULE-036: 'B' -> pa_sat_pnotificacion(@i_canal RAW 'BNK', account, type, service, order, group ref, sequence, ORDERED value, beneficiary, separate commission, NULL credit data)")
    void rule036_basicNotificationArguments() {
        FakeAseSession ase = basic();

        DebitResult result = ase.service().debit(rolpago().canal("BNK").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        BasicNotificationCommand sent = ase.onlyBasicNotification();
        assertThat(sent.iCanal()).as("@i_canal = @i_canal: the raw channel, not @w_canal_sms 'IBK'").isEqualTo("BNK");
        assertThat(sent.iCtadebito()).isEqualTo(Requests.ACCOUNT);
        assertThat(sent.iTipctadeb()).isEqualTo(3);
        assertThat(sent.iServicio()).isEqualTo("ROLPAGO");
        assertThat(sent.iOrdenBanco()).isEqualTo(ORDER);
        assertThat(sent.iDireccionTransf()).isEqualTo("GRP000001");
        assertThat(sent.iSecuencial()).isEqualTo(3);
        assertThat(sent.iValor()).as("@i_valor = @i_valor_ordenado, not the debit value").isEqualByComparingTo("250.00");
        assertThat(sent.iNombrecred()).isEqualTo("BENEFICIARIO FAKE UNO");
        assertThat(sent.iComision()).as("@i_comision = @i_valor_comision").isEqualByComparingTo("1.20");
        assertThat(sent.iCtacred()).isNull();
        assertThat(sent.iProdCre()).isNull();
        assertThat(sent.iEmpresa()).isNull();
        assertThat(ase.events()).isEmpty();
        assertThat(ase.notificationLog()).containsExactly(
                "ad_servicios_sms", "ad_notificacion_basica", "bp_detalle:beneficiary", "pa_sat_pnotificacion");
        assertThat(ase.ownerQueries()).as("no debtor lookup on the 'B' path").isEmpty();
        assertThat(ase.blockQueries()).as("the block list applies only to 'OTRO'").isEmpty();
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "pa_sat_pnotificacion",
                "sp_grb_mov_y_frmpgo", "sp_grb_comision");
    }

    @Test
    @DisplayName("RULE-036 + RULE-027: the commission sent is the NORMALIZED @i_valor_comision (bundled 0.75 with separate 0 -> 0.75)")
    void rule036_commissionIsNormalizedSeparateCommission() {
        FakeAseSession ase = basic();

        ase.service().debit(rolpago().comision("0.75").valorComision("0").build());

        assertThat(ase.onlyBasicNotification().iComision()).isEqualByComparingTo("0.75");
    }

    @Test
    @DisplayName("RULE-036: the classification read uses the RAW service (exact code), and the beneficiary read the order")
    void rule036_readArguments() {
        FakeAseSession ase = basic();

        ase.service().debit(rolpago().build());

        assertThat(ase.classQueries()).containsExactly("ROLPAGO");
        assertThat(ase.beneficiaryQueries()).containsExactly(new OrderQuery(DetailTable.LIVE, ORDER));
    }

    @Test
    @DisplayName("RULE-036 + RULE-024: beneficiary only in db_sat_his (@@rowcount = 0 live) -> read there and used")
    void rule036_beneficiaryHistoryFallback() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", "B")
                .beneficiaryDetail(DetailTable.HISTORY, ORDER, "BENEFICIARIO HISTORICO", "GRPHIS001");

        ase.service().debit(rolpago().build());

        assertThat(ase.beneficiaryQueries()).containsExactly(new OrderQuery(DetailTable.LIVE, ORDER),
                new OrderQuery(DetailTable.HISTORY, ORDER));
        assertThat(ase.onlyBasicNotification().iNombrecred()).isEqualTo("BENEFICIARIO HISTORICO");
        assertThat(ase.onlyBasicNotification().iDireccionTransf()).isEqualTo("GRPHIS001");
    }

    @Test
    @DisplayName("RULE-036: beneficiary found nowhere -> NULL name and group reference, pa_sat_pnotificacion still called")
    void rule036_beneficiaryNotFound() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", "B");

        ase.service().debit(rolpago().build());

        assertThat(ase.onlyBasicNotification().iNombrecred()).isNull();
        assertThat(ase.onlyBasicNotification().iDireccionTransf()).isNull();
    }

    @Test
    @DisplayName("RULE-036: the group reference goes through @w_dt_referencia_grupo varchar(20); the name through varchar(64)")
    void rule036_variableWidths() {
        String name70 = "N".repeat(70);
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", "B")
                .beneficiaryDetail(DetailTable.LIVE, ORDER, name70, "GRP-ABCDEFGHIJKLMNOPQRSTUVWXYZ");

        ase.service().debit(rolpago().build());

        assertThat(ase.onlyBasicNotification().iDireccionTransf()).isEqualTo("GRP-ABCDEFGHIJKLMNOP");
        assertThat(ase.onlyBasicNotification().iNombrecred()).isEqualTo("N".repeat(64));
    }

    @Test
    @DisplayName("RULE-036 + RULE-003 + RULE-004: 'B' for TRANSCLI sends the interbank credit account, product and institution; the movement gets the ordered value")
    void rule036_rule003_interbankBasicNotification() {
        FakeAseSession ase = new FakeAseSession().smsService("TRC-SAT", "NOTIFICA TRANSCLI").notificationClassRow("TRANSCLI", "B")
                .bceInstitution("000000017BANCO DESTINO FAKE")
                .interbankDetail(DetailTable.LIVE, ORDER, "000000017", 4, "2200334455");

        ase.service().debit(Requests.currentAccount().servicio("TRANSCLI").valorDebito("100.50").valorOrdenado("100.00")
                .comision("0.50").valorComision(null).build());

        BasicNotificationCommand sent = ase.onlyBasicNotification();
        assertThat(sent.iCtacred()).isEqualTo("2200334455");
        assertThat(sent.iProdCre()).isEqualTo("AHO");
        assertThat(sent.iEmpresa()).isEqualTo("BANCO DESTINO FAKE");
        assertThat(sent.iValor()).isEqualByComparingTo("100.00");
        assertThat(sent.iComision()).as("@i_valor_comision, NOT the interbank @w_valor_comision").isNull();
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
    }

    // ---- classification (994-1014) -------------------------------------------------------------

    @ParameterizedTest(name = "RULE-036: classification ''{0}'' -> 'B' path (varchar(4), trailing blanks ignored)")
    @ValueSource(strings = {"B", "B  ", "B   X"})
    void rule036_valuesThatMeanB(String value) {
        // 'B   X' is cut to varchar(4) 'B   ', which equals 'B'
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", value);

        ase.service().debit(rolpago().build());

        assertThat(ase.basicNotifications()).hasSize(1);
        assertThat(ase.events()).isEmpty();
    }

    @ParameterizedTest(name = "RULE-036: classification ''{0}'' -> 'OTRO' path (sp_eventos)")
    @ValueSource(strings = {"OTRO", "OTROS", "OTRO-X"})
    void rule036_valuesThatMeanOtro(String value) {
        // varchar(4): 'OTROS' and 'OTRO-X' are cut to 'OTRO'
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", value)
                .currentOwner(Requests.ACCOUNT, 1);

        ase.service().debit(rolpago().build());

        assertThat(ase.events()).hasSize(1);
        assertThat(ase.basicNotifications()).isEmpty();
    }

    @Test
    @DisplayName("RULE-036: no classification row, or a row with a NULL value -> default 'OTRO' (sp_eventos)")
    void rule036_defaultOtro() {
        FakeAseSession noRow = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").currentOwner(Requests.ACCOUNT, 1);
        FakeAseSession nullValue = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO")
                .notificationClassRow("ROLPAGO", null).currentOwner(Requests.ACCOUNT, 1);

        noRow.service().debit(rolpago().build());
        nullValue.service().debit(rolpago().build());

        assertThat(noRow.events()).hasSize(1);
        assertThat(nullValue.events()).hasSize(1);
    }

    @ParameterizedTest(name = "RULE-036: classification ''{0}'' -> no notifier at all, @w_return stays 0, commit")
    @ValueSource(strings = {"X", "BX", " B", "OTR", "BASICA"})
    void rule036_anyOtherValueSendsNothing(String value) {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", value)
                .currentOwner(Requests.ACCOUNT, 1).beneficiaryDetail(DetailTable.LIVE, ORDER, "N", "G");

        DebitResult result = ase.service().debit(rolpago().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.events()).isEmpty();
        assertThat(ase.basicNotifications()).isEmpty();
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms", "ad_notificacion_basica");
        assertThat(ase.commits()).isEqualTo(1);
    }

    // ---- outcome and A15 -----------------------------------------------------------------------

    @Test
    @DisplayName("A15 + RULE-036: step outcome on the 'B' path carries the notifier's @o_error; the beneficiary read does not touch @wRowdbBiz")
    void a15_stepOutcomeCarriesNotifierOError() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").notificationClassRow("ROLPAGO", "B")
                .beneficiaryDetail(DetailTable.HISTORY, ORDER, "N", "G").basicNotification(0, 5, "AVISO FAKE");
        DebitRequest request = rolpago().build();

        NotificationOutcome outcome = new CustomerNotifications(ase, ase, ase, ase, ase).notify(new NotificationContext(
                request, request.iServicio(), 2701, "0150", request.iValorDebito(), BigDecimal.ZERO, new BigDecimal("1.20"), 9001));

        assertThat(outcome).isEqualTo(new NotificationOutcome(true, 0, null, 5, null));
    }

    @Test
    @DisplayName("A15: pa_sat_pnotificacion returns 0 with @o_error 5 -> the debit commits and answers (0, 5)")
    void a15_successReturnsNotifierOError() {
        FakeAseSession ase = basic().basicNotification(0, 5, "AVISO FAKE");

        DebitResult result = ase.service().debit(rolpago().build());

        assertThat(result).isEqualTo(new DebitResult(0, 5, null));
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyMovement().iCodError()).as("@w_cod_errord = @w_return = 0").isEqualTo(0);
        assertThat(ase.committedProcedures()).contains("sp_ndc_ahcc", "pa_sat_pnotificacion", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("A15: same in COBIS mode (aplcobis 'S'): (0, 5), no sp_cerror")
    void a15_successReturnsNotifierOErrorCobis() {
        FakeAseSession ase = basic().basicNotification(0, 5, null);

        DebitResult result = ase.service().debit(rolpago().aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(0, 5, null));
        assertThat(ase.errorReports()).isEmpty();
    }

    @Test
    @DisplayName("A15: a NULL notifier @o_error -> isnull(@o_error, 0) -> (0, 0)")
    void a15_nullNotifierOErrorBecomesZeroOnSuccess() {
        FakeAseSession ase = basic().basicNotification(0, null, null);

        DebitResult result = ase.service().debit(rolpago().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
    }

    @Test
    @DisplayName("A15 + RULE-010 + RULE-028: exit C in COBIS mode does not assign @o_error -> (122004, 5) and sp_cerror(122004)")
    void a15_exitCCobisKeepsNotifierOError() {
        FakeAseSession ase = basic().basicNotification(0, 5, null).noHeaderRows();

        DebitResult result = ase.service().debit(rolpago().aplcobis("S").spName("sp_caller_fake").opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(122004, 5, null));
        assertThat(ase.onlyErrorReport()).isEqualTo(new ErrorReport("sp_caller_fake", 122004));
        assertThat(ase.committedWrites()).as("full rollback: the notification is gone too").isEmpty();
    }

    @Test
    @DisplayName("A15 + RULE-028: exit C in COBIS mode after a NULL notifier @o_error -> (122004, NULL): no isnull on that exit")
    void a15_exitCCobisKeepsNullNotifierOError() {
        FakeAseSession ase = basic().basicNotification(0, null, null).noHeaderRows();

        DebitResult result = ase.service().debit(rolpago().aplcobis("S").opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(122004, null, null));
    }

    @Test
    @DisplayName("A15 + RULE-015: movement write fails (122002) in COBIS mode -> (122002, 5)")
    void a15_exitCMovementFailureCobisKeepsNotifierOError() {
        FakeAseSession ase = basic().basicNotification(0, 5, null).movement(1);

        DebitResult result = ase.service().debit(rolpago().aplcobis("S").build());

        assertThat(result).isEqualTo(new DebitResult(122002, 5, null));
    }

    @Test
    @DisplayName("A15 + RULE-028: exit C in the non-COBIS mode overwrites @o_error -> (0, 122004)")
    void a15_exitCNonCobisOverwrites() {
        FakeAseSession ase = basic().basicNotification(0, 5, null).noHeaderRows();

        DebitResult result = ase.service().debit(rolpago().aplcobis("N").opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
    }

    @Test
    @DisplayName("A15 + RULE-013: exit B overwrites @o_error (122010, 122010); with A14 (return 1, o_error 0) -> (0, 0), not 5")
    void a15_exitBOverwrites() {
        FakeAseSession failed = basic().basicNotification(0, 5, null).commission(0, 122010);
        FakeAseSession a14 = basic().basicNotification(0, 5, null).commission(1, 0);

        DebitResult r1 = failed.service().debit(rolpago().build());
        DebitResult r2 = a14.service().debit(rolpago().build());

        assertThat(r1).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(r2).isEqualTo(new DebitResult(0, 0, null));
    }

    @Test
    @DisplayName("RULE-034 + RULE-012: pa_sat_pnotificacion returns 40002 -> exit A (40002, 40002) even when its @o_error was 0; savepoint rollback removes debit and notification")
    void rule034_rule012_basicNotifierFailureIsExitA() {
        FakeAseSession ase = basic().basicNotification(40002, 0, "FALLO FAKE");

        DebitResult result = ase.service().debit(rolpago().build());

        assertThat(result).isEqualTo(new DebitResult(40002, 40002, null));
        assertThat(ase.calls()).containsSubsequence("sp_ndc_ahcc", "notificationStep", "pa_sat_pnotificacion",
                "rollbackToSavepoint:sp_debito_empresa", "sp_grb_mov_y_frmpgo", "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(40002);
        assertThat(ase.commissionSteps()).isEmpty();
    }
}
