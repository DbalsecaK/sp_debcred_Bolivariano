package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.SmsQuery;
import com.nexti.debcred.support.Requests;

/**
 * Block B7 entry, channel and SMS service (sp_debcred_empresa.sp:746-814), RULE-035 and RULE-034:
 * <pre>
 * if @w_return = 0 and ltrim(rtrim(@i_servicio)) &lt;&gt; 'PAGOPRV'
 *    select @w_canal_sms = @i_canal                       -- char(3)
 *    DIR/SAT -&gt; 'SAT'/'SAT'; BNK -&gt; 'IBK'/'24OnLine'; VEN -&gt; 'Ventanilla'
 *    select @w_serv_sms = ltrim(rtrim(substring(ct_cod_catalogo, 1, patindex('%-%', ct_cod_catalogo) - 1)))
 *      from ba_tabla, ba_catalogo where tb_nom_tabla = 'ad_servicios_sms'
 *       and ct_nom_catalogo like '%' + ltrim(rtrim(@i_servicio)) + '%'
 *       and right(ltrim(rtrim(ct_cod_catalogo)), 3) = @w_canal_sms and ct_est_catalogo = 'A'
 *    if @w_serv_sms is not null ... (the rest of B7) ... select @w_cod_errord = @w_return
 * </pre>
 * Brief section 7 A16: several matching rows -> the lowest {@code ct_cod_catalogo}. Derived from ASE
 * semantics (to confirm with the bank's recorded outputs, A6): a code without '-' is
 * {@code substring(code, 1, -1)} = NULL, so a matching row can still leave the notification unconfigured;
 * an all-blank service trims to NULL, so line 746 is unknown and B7 is not entered. Fake data only.
 */
class Rule035Rule034NotificationChannelAndSmsServiceTest {

    private static final String ACCOUNT = Requests.ACCOUNT;

    /** A ROLPAGO debit on the given channel whose catalogue has one SMS row with that suffix. */
    private static FakeAseSession rolpagoSms(String suffix) {
        return new FakeAseSession().smsService("ROL-" + suffix, "NOTIFICACION ROLPAGO").currentOwner(ACCOUNT, 700001);
    }

    private static String rtrim(String s) {
        return s == null ? null : s.replaceAll(" +$", "");
    }

    // ---- entry (line 746) ---------------------------------------------------------------------

    @Test
    @DisplayName("RULE-034 entry: a successful debit with an SMS row notifies (sp_eventos inside the transaction) and still commits 0/0")
    void rule034_successfulDebitWithSmsRowNotifies() {
        FakeAseSession ase = rolpagoSms("SAT");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal("DIR").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyEvent()).isNotNull();
        assertThat(ase.calls()).containsSubsequence("begin", "sp_ndc_ahcc", "notificationStep", "sp_eventos",
                "sp_grb_mov_y_frmpgo", "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_eventos", "sp_grb_mov_y_frmpgo");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(0);
    }

    @Test
    @DisplayName("RULE-034 entry: a failed debit never reads the notification catalogue (line 746: @w_return = 0)")
    void rule034_failedDebitReadsNothing() {
        FakeAseSession ase = rolpagoSms("SAT").debitNote(201045, null);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(201045, 201045, null));
        assertThat(ase.notificationLog()).isEmpty();
        assertThat(ase.events()).isEmpty();
    }

    @ParameterizedTest(name = "RULE-034 entry: service ''{0}'' never enters B7, even with a catch-all SMS row")
    @ValueSource(strings = {"PAGOPRV", " PAGOPRV ", "   "})
    void rule034_pagoprvAndBlankServiceReadNothing(String service) {
        // "   ": ltrim(rtrim('   ')) is NULL in ASE, so "NULL <> 'PAGOPRV'" is unknown and B7 is skipped (derived)
        FakeAseSession ase = new FakeAseSession().smsService("ANY-SAT", "CUALQUIER SERVICIO PAGOPRV")
                .currentOwner(ACCOUNT, 700001);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio(service).opcion("04").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.notificationLog()).isEmpty();
        assertThat(ase.events()).isEmpty();
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
    }

    @Test
    @DisplayName("RULE-034 entry: a NULL service never enters B7 (NULL <> 'PAGOPRV' is unknown), even with a catch-all SMS row")
    void rule034_nullServiceReadsNothing() {
        FakeAseSession ase = new FakeAseSession().smsService("ANY-SAT", "CUALQUIER SERVICIO").currentOwner(ACCOUNT, 700001);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio(null).opcion("04").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.notificationLog()).isEmpty();
        assertThat(ase.events()).isEmpty();
    }

    @Test
    @DisplayName("RULE-034 entry: ledger accounts (type 9) never reach B7, even with an SMS row")
    void rule034_ledgerAccountReadsNothing() {
        FakeAseSession ase = rolpagoSms("SAT");

        ase.service().debit(Requests.currentAccount().tipctaEmp(9).servicio("ROLPAGO").build());

        assertThat(ase.notificationLog()).isEmpty();
        assertThat(ase.onlyLedgerDebit()).isNotNull();
    }

    @Test
    @DisplayName("RULE-034: the step receives exactly one catalogue read per debit and runs between the debit and the movement")
    void rule034_stepRunsBetweenDebitAndMovement() {
        FakeAseSession ase = rolpagoSms("SAT");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(ase.smsQueries()).hasSize(1);
        assertThat(ase.calls()).containsSubsequence("sp_ndc_ahcc", "notificationStep", "sp_eventos", "sp_grb_mov_y_frmpgo");
    }

    // ---- channel (760-794) ---------------------------------------------------------------------

    @ParameterizedTest(name = "RULE-035: channel ''{0}'' -> @w_canal_sms ''{1}'', @w_desc_canal ''{2}'' (catalogue suffix ''{1}'')")
    @CsvSource(value = {
            "DIR, SAT, SAT",
            "SAT, SAT, SAT",
            "BNK, IBK, 24OnLine",
            "VEN, VEN, Ventanilla",
            "SFR, SFR, NULL",
            "FR2, FR2, NULL",
            "BTH, BTH, NULL",
            "IBK, IBK, NULL",
            "BCE, BCE, NULL",
            "'DIR ', SAT, SAT",
            "BNKX, IBK, 24OnLine",
            "VENTANILLA, VEN, Ventanilla"}, nullValues = "NULL")
    void rule035_channelMapping(String canal, String canalSms, String descCanal) {
        // @w_canal_sms char(3) := @i_canal: the first three characters ('BNKX' -> 'BNK')
        FakeAseSession ase = rolpagoSms(canalSms);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal(canal).build());

        assertThat(rtrim(ase.onlySmsQuery().canalSms())).as("canal passed to the catalogue read").isEqualTo(canalSms);
        assertThat(rtrim(ase.onlyEvent().iCanal())).as("@i_canal of sp_eventos").isEqualTo(canalSms);
        assertThat(ase.onlyEvent().iDescCanal()).isEqualTo(descCanal);
        assertThat(ase.onlyEvent().iServicio()).isEqualTo("ROL");
    }

    @Test
    @DisplayName("RULE-035: a two-character channel pads to char(3) ('SA ') and can never equal a 3-character catalogue suffix")
    void rule035_shortChannelNeverMatches() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SA", "NOTIFICACION ROLPAGO")
                .smsService("ROLSA", "NOTIFICACION ROLPAGO").currentOwner(ACCOUNT, 700001);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal("SA").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(rtrim(ase.onlySmsQuery().canalSms())).isEqualTo("SA");
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms");
        assertThat(ase.events()).isEmpty();
    }

    @Test
    @DisplayName("RULE-035: the DIR channel reads the catalogue with suffix 'SAT' and the RAW service (the port trims it)")
    void rule035_catalogueReadArguments() {
        FakeAseSession ase = new FakeAseSession().smsService("TRC-SAT", "NOTIFICA TRANSCLI").currentOwner(ACCOUNT, 1);

        ase.service().debit(Requests.currentAccount().servicio(" TRANSCLI ").canal("DIR").build());

        assertThat(ase.onlySmsQuery()).isEqualTo(new SmsQuery(" TRANSCLI ", "SAT"));
        assertThat(ase.onlyEvent().iServicio()).as("like '%' + ltrim(rtrim(@i_servicio)) + '%' found the row").isEqualTo("TRC");
    }

    // ---- SMS service (798-814) -----------------------------------------------------------------

    @Test
    @DisplayName("RULE-035: no ad_servicios_sms row -> not configured: nothing else is read, no notifier, code 0, commit")
    void rule035_noSmsRowIsNotConfigured() {
        FakeAseSession ase = new FakeAseSession().currentOwner(ACCOUNT, 700001).notificationClassRow("ROLPAGO", "B");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms");
        assertThat(ase.events()).isEmpty();
        assertThat(ase.basicNotifications()).isEmpty();
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-035: a row for another channel ('ROL-IBK' on DIR -> 'SAT') does not configure the notification")
    void rule035_rowForAnotherChannelDoesNotMatch() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-IBK", "NOTIFICACION ROLPAGO").currentOwner(ACCOUNT, 1);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").canal("DIR").build());

        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms");
        assertThat(ase.events()).isEmpty();
    }

    @Test
    @DisplayName("RULE-035: LIKE '%SPI%' also matches another service's row whose name merely contains 'SPI' (preserved)")
    void rule035_likeMatchesAContainedName() {
        FakeAseSession ase = new FakeAseSession().smsService("SPX-SAT", "ALERTAS SPIDER").currentOwner(ACCOUNT, 1);

        ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("10").build());

        assertThat(ase.onlyEvent().iServicio()).isEqualTo("SPX");
    }

    @Test
    @DisplayName("RULE-035: @w_serv_sms is the trimmed code prefix before the first '-' (' ROL -SAT' -> 'ROL', 'A-B-SAT' -> 'A')")
    void rule035_prefixBeforeFirstDashTrimmed() {
        FakeAseSession padded = new FakeAseSession().smsService(" ROL -SAT", "NOTIFICACION ROLPAGO").currentOwner(ACCOUNT, 1);
        FakeAseSession twoDashes = new FakeAseSession().smsService("A-B-SAT", "NOTIFICACION ROLPAGO").currentOwner(ACCOUNT, 1);

        padded.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());
        twoDashes.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(padded.onlyEvent().iServicio()).isEqualTo("ROL");
        assertThat(twoDashes.onlyEvent().iServicio()).isEqualTo("A");
    }

    @Test
    @DisplayName("A16: several matching rows -> the LOWEST ct_cod_catalogo gives @w_serv_sms ('ROLA-SAT' before 'ROLB-SAT')")
    void a16_lowestCatalogueCodeWins() {
        FakeAseSession ase = new FakeAseSession()
                .smsService("ROLB-SAT", "NOTIFICACION ROLPAGO B")
                .smsService("ROLA-SAT", "NOTIFICACION ROLPAGO A")
                .smsService("ROLC-SAT", "NOTIFICACION ROLPAGO C")
                .currentOwner(ACCOUNT, 1);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(ase.onlyEvent().iServicio()).isEqualTo("ROLA");
    }

    @ParameterizedTest(name = "RULE-035 (derived, ASE substring): code ''{0}'' gives a NULL @w_serv_sms -> not configured")
    @ValueSource(strings = {"ROLSAT", "-SAT", "  -SAT"})
    void rule035_codeWithoutUsablePrefixIsNotConfigured(String code) {
        // no '-': substring(code, 1, 0 - 1) = NULL; leading '-': length 0 = NULL; blank prefix: ltrim(rtrim) = NULL
        FakeAseSession ase = new FakeAseSession().smsService(code, "NOTIFICACION ROLPAGO").currentOwner(ACCOUNT, 1);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms");
        assertThat(ase.events()).isEmpty();
    }

    @Test
    @DisplayName("A16 + RULE-035: the lowest row is chosen FIRST; when it has no '-' the notification is not configured even if a higher row has a prefix")
    void a16_lowestRowWithoutDashLeavesNotConfigured() {
        FakeAseSession ase = new FakeAseSession()
                .smsService("ROL-SAT", "NOTIFICACION ROLPAGO")
                .smsService("AAASAT", "NOTIFICACION ROLPAGO")
                .currentOwner(ACCOUNT, 1);

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(ase.notificationLog()).containsExactly("ad_servicios_sms");
        assertThat(ase.events()).isEmpty();
    }

    @Test
    @DisplayName("RULE-035: not configured leaves the TRANSCLI debit value alone (RULE-003 override not reached) and reads no detail")
    void rule035_notConfiguredKeepsTheDebitValue() {
        FakeAseSession ase = new FakeAseSession()
                .bceInstitution("000000017BANCO DESTINO FAKE")
                .interbankDetail(FakeAseSession.DetailTable.LIVE, Requests.BANK_ORDER, "000000017", 4, "2200334455");

        ase.service().debit(Requests.currentAccount().servicio("TRANSCLI").valorDebito("100.50").valorOrdenado("100.00").build());

        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.50");
        assertThat(ase.interbankQueries()).isEmpty();
    }

    @Test
    @DisplayName("RULE-034/035: the step's outcome when not configured is exactly notConfigured() (false, 0, null, 0, null)")
    void rule035_stepOutcomeWhenNotConfigured() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().servicio("ROLPAGO").build();

        NotificationOutcome outcome = new CustomerNotifications(ase, ase, ase, ase, ase).notify(
                new NotificationContext(request, request.iServicio(), 2701, "0150", request.iValorDebito(),
                        BigDecimal.ZERO, BigDecimal.ZERO, 9001));

        assertThat(outcome).isEqualTo(NotificationOutcome.notConfigured());
        assertThat(outcome).isEqualTo(new NotificationOutcome(false, 0, null, 0, null));
    }

    @Test
    @DisplayName("RULE-034: configured + sp_eventos returned 0 -> outcome (true, 0, no value replacement, @o_error 0, @wRowdbBiz NULL)")
    void rule034_stepOutcomeWhenConfiguredAndNotified() {
        FakeAseSession ase = rolpagoSms("SAT");
        DebitRequest request = Requests.currentAccount().servicio("ROLPAGO").build();

        NotificationOutcome outcome = new CustomerNotifications(ase, ase, ase, ase, ase).notify(
                new NotificationContext(request, request.iServicio(), 2701, "0150", request.iValorDebito(),
                        BigDecimal.ZERO, BigDecimal.ZERO, 9001));

        assertThat(outcome).isEqualTo(new NotificationOutcome(true, 0, null, 0, null));
        assertThat(ase.onlyEvent()).isNotNull();
    }
}
