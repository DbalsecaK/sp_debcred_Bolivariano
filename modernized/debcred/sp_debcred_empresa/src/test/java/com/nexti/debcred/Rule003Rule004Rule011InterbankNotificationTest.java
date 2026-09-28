package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.DetailTable;
import com.nexti.debcred.support.FakeAseSession.OrderQuery;
import com.nexti.debcred.support.Requests;

/**
 * RULE-003, RULE-004, RULE-011 (sp_debcred_empresa.sp:878-976) and the credit-product part of RULE-005:
 * for TRANSCLI, TARJCRED and COMEXT (trimmed), once the SMS service is configured,
 * <pre>
 * select @i_comision, @i_valor_ordenado              -- line 882: NOT emitted (brief 7 A11, approved difference)
 * select @w_valor_comision = @i_comision              -- the normalized bundled commission
 * select @i_valor_debito   = @i_valor_ordenado        -- flows out of B7: the movement (1424) uses it
 * select @w_emp_inst = substring(ct_nom_catalogo, 10, 32), @w_tip_cta = dt_tipo_cta, @w_cta_cre = dt_numero_cuenta
 *   from bp_detalle, ba_tabla, ba_catalogo      -- ad_cuentas_bce, joined on the 9-character group reference
 *  (live, then db_sat_his when the live read returned no row)
 * @w_tip_cta 0 -&gt; NULL, 3 -&gt; CTE, 4 -&gt; AHO; then, outside the branch, 9 -&gt; CON, 8 -&gt; ESP
 * </pre>
 * The debit note already went out with the original value; only the movement and the notification see
 * the ordered value. Several live rows (the join is by order only) keep an undefined row in the legacy:
 * only the rowcount and the row-consistency of the three fields are pinned. Fake data only.
 */
class Rule003Rule004Rule011InterbankNotificationTest {

    private static final int ORDER = Requests.BANK_ORDER;
    private static final String GROUP = "000000017";
    private static final String BCE_NAME = GROUP + "BANCO DESTINO FAKE";

    /** SMS rows for the three interbank services on channel DIR ('SAT'), the BCE institution and the debtor owner. */
    private static FakeAseSession interbank() {
        return new FakeAseSession()
                .smsService("TRC-SAT", "NOTIFICA TRANSCLI")
                .smsService("TJC-SAT", "NOTIFICA TARJCRED")
                .smsService("CEX-SAT", "NOTIFICA COMEXT")
                .bceInstitution(BCE_NAME)
                .currentOwner(Requests.ACCOUNT, 700001);
    }

    private static FakeAseSession withLiveDetail(Integer tipoCta) {
        return interbank().interbankDetail(DetailTable.LIVE, ORDER, GROUP, tipoCta, "2200334455");
    }

    private static Requests transcli() {
        return Requests.currentAccount().servicio("TRANSCLI").canal("DIR").valorDebito("100.50").valorOrdenado("100.00")
                .comision("0.50").valorComision(null);
    }

    // ---- RULE-003 / RULE-011: the debit value override ----------------------------------------

    @Test
    @DisplayName("RULE-003 (brief example): debit 100.50, ordered 100.00, commission 0.50 -> debit note 100.50, movement 100.00, event value '100.00', cost '0.50'")
    void rule003_orderedValueReplacesTheDebitValueForTheMovement() {
        FakeAseSession ase = withLiveDetail(4);

        DebitResult result = ase.service().debit(transcli().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyDebitNote().iValor()).as("the debit note already went out").isEqualByComparingTo("100.50");
        assertThat(ase.onlyMovement().iValorMov()).as("sp_grb_mov_y_frmpgo @i_valor_mov").isEqualByComparingTo("100.00");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyEvent().iValor()).isEqualTo("100.00");
        assertThat(ase.onlyEvent().iCosto()).isEqualTo("0.50");
    }

    @ParameterizedTest(name = "RULE-003: service ''{0}'' replaces the working debit value by the ordered value")
    @ValueSource(strings = {"TRANSCLI", "TARJCRED", "COMEXT", " TRANSCLI ", "COMEXT  "})
    void rule003_allThreeServicesTrimmed(String service) {
        FakeAseSession ase = withLiveDetail(4);

        ase.service().debit(transcli().servicio(service).build());

        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
        assertThat(ase.interbankQueries()).containsExactly(new OrderQuery(DetailTable.LIVE, ORDER));
    }

    @ParameterizedTest(name = "RULE-003: service ''{0}'' keeps the original debit value")
    @ValueSource(strings = {"ROLPAGO", "TRANSWIFT", "TRANSCLIX", "TRANSCL"})
    void rule003_otherServicesKeepTheDebitValue(String service) {
        FakeAseSession ase = withLiveDetail(4).smsService("GEN-SAT", "NOTIFICA ROLPAGO TRANSWIFT TRANSCLIX TRANSCL");

        ase.service().debit(transcli().servicio(service).build());

        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.50");
        assertThat(ase.interbankQueries()).isEmpty();
    }

    @Test
    @DisplayName("RULE-003 edge: with no SMS configuration the override does not run (movement keeps 100.50)")
    void rule003_noSmsConfigurationNoOverride() {
        FakeAseSession ase = new FakeAseSession().bceInstitution(BCE_NAME)
                .interbankDetail(DetailTable.LIVE, ORDER, GROUP, 4, "2200334455");

        ase.service().debit(transcli().build());

        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.50");
    }

    @Test
    @DisplayName("RULE-003 (brief 7 A19-b): a NULL ordered value replaces the debit value too; the movement carries NULL, the event value is NULL")
    void rule003_nullOrderedValueReplacesTheDebitValueWithNull() {
        FakeAseSession ase = withLiveDetail(4);

        DebitResult result = ase.service().debit(transcli().valorOrdenado(null).build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyDebitNote().iValor()).isEqualByComparingTo("100.50");
        assertThat(ase.onlyMovement().iValorMov()).as("886: select @i_valor_debito = @i_valor_ordenado (NULL)").isNull();
        assertThat(ase.onlyEvent().iValor()).isNull();
    }

    @Test
    @DisplayName("RULE-003 + RULE-012: when the notifier then fails, the 'X' movement of exit A also carries the ordered value")
    void rule003_rule012_exitAMovementCarriesTheOrderedValue() {
        FakeAseSession ase = withLiveDetail(4).event(30001);

        DebitResult result = ase.service().debit(transcli().build());

        assertThat(result).isEqualTo(new DebitResult(30001, 30001, null));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("RULE-003 + RULE-036: the override also happens when the classification sends no notification at all ('X')")
    void rule003_rule036_overrideIndependentOfClassification() {
        FakeAseSession ase = withLiveDetail(4).notificationClassRow("TRANSCLI", "X");

        DebitResult result = ase.service().debit(transcli().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.events()).isEmpty();
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("RULE-011 (A11): the step's outcome carries the ordered value as the new debit value; no result set is modelled")
    void rule011_stepOutcomeCarriesTheOrderedValue() {
        FakeAseSession ase = withLiveDetail(4);
        DebitRequest request = transcli().build();

        NotificationOutcome outcome = new CustomerNotifications(ase, ase, ase, ase, ase).notify(new NotificationContext(
                request, request.iServicio(), 2701, "0150", request.iValorDebito(), new BigDecimal("0.50"), null, 9001));

        assertThat(outcome.configured()).isTrue();
        assertThat(outcome.returnCode()).isZero();
        assertThat(outcome.valorDebito()).isEqualByComparingTo("100.00");
        assertThat(outcome.oError()).isEqualTo(0);
        assertThat(outcome.wRowdbBiz()).isEqualTo(1);
    }

    // ---- RULE-004: commission, institution, credit account, credit product --------------------

    @Test
    @DisplayName("RULE-004 + RULE-027: the notification commission is the NORMALIZED @i_comision: 0.50 with separate 0 -> moved away -> cost '0.00'")
    void rule004_commissionIsNormalizedBundledCommission() {
        FakeAseSession normalized = withLiveDetail(4);
        FakeAseSession separate = withLiveDetail(4);

        normalized.service().debit(transcli().comision("0.50").valorComision("0").build());
        separate.service().debit(transcli().comision("0.50").valorComision("2.00").build());

        assertThat(normalized.onlyEvent().iCosto()).isEqualTo("0.00");
        assertThat(separate.onlyEvent().iCosto()).as("@i_valor_comision is ignored for these services").isEqualTo("0.50");
    }

    @Test
    @DisplayName("RULE-004: institution = substring(ct_nom_catalogo, 10, 32), credit account = dt_numero_cuenta")
    void rule004_institutionAndCreditAccount() {
        FakeAseSession ase = withLiveDetail(4);

        ase.service().debit(transcli().build());

        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("BANCO DESTINO FAKE");
        assertThat(ase.onlyEvent().iCtaCre()).isEqualTo("2200334455");
    }

    @Test
    @DisplayName("RULE-004: the institution is at most 32 characters (positions 10-41 of the catalogue name)")
    void rule004_institutionTruncatedTo32() {
        FakeAseSession ase = interbank().bceInstitution("000000099" + "BANCOINTERNACIONALDELPACIFICOFAKESA")   // 9 + 35
                .interbankDetail(DetailTable.LIVE, ORDER, "000000099", 4, "1");

        ase.service().debit(transcli().build());

        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("BANCOINTERNACIONALDELPACIFICOFAK").hasSize(32);
    }

    @Test
    @DisplayName("RULE-004 (derived, ASE substring): a catalogue name of exactly 9 characters joins but gives a NULL institution")
    void rule004_nineCharacterNameGivesNullInstitution() {
        FakeAseSession ase = new FakeAseSession().smsService("TRC-SAT", "NOTIFICA TRANSCLI").bceInstitution("000000055")
                .interbankDetail(DetailTable.LIVE, ORDER, "000000055", 3, "3300").currentOwner(Requests.ACCOUNT, 1);

        ase.service().debit(transcli().build());

        assertThat(ase.onlyEvent().iEmpresa()).isNull();
        assertThat(ase.onlyEvent().iCtaCre()).isEqualTo("3300");
        assertThat(ase.onlyEvent().iProdCre()).isEqualTo("CTE");
    }

    @Test
    @DisplayName("RULE-004: a credit account longer than 30 characters is cut to @w_cta_cre varchar(30)")
    void rule004_creditAccountTruncatedTo30() {
        FakeAseSession ase = interbank().interbankDetail(DetailTable.LIVE, ORDER, GROUP, 4, "12345678901234567890123456789012");

        ase.service().debit(transcli().build());

        assertThat(ase.onlyEvent().iCtaCre()).isEqualTo("123456789012345678901234567890");
    }

    @ParameterizedTest(name = "RULE-004 + RULE-005: dt_tipo_cta {0} -> credit product {1}")
    @CsvSource(value = {"0, NULL", "3, CTE", "4, AHO", "8, ESP", "9, CON", "5, NULL", "1, NULL", "NULL, NULL"}, nullValues = "NULL")
    void rule004_rule005_creditProductMap(Integer tipoCta, String prodCre) {
        FakeAseSession ase = withLiveDetail(tipoCta);

        ase.service().debit(transcli().build());

        assertThat(ase.onlyEvent().iProdCre()).isEqualTo(prodCre);
    }

    @Test
    @DisplayName("RULE-004 + RULE-024: detail only in db_sat_his -> read there (same order), its values used, override still applied")
    void rule004_historyFallback() {
        FakeAseSession ase = interbank().interbankDetail(DetailTable.HISTORY, ORDER, GROUP, 3, "HIS-0099");

        ase.service().debit(transcli().build());

        assertThat(ase.interbankQueries()).containsExactly(new OrderQuery(DetailTable.LIVE, ORDER),
                new OrderQuery(DetailTable.HISTORY, ORDER));
        assertThat(ase.onlyEvent().iCtaCre()).isEqualTo("HIS-0099");
        assertThat(ase.onlyEvent().iProdCre()).isEqualTo("CTE");
        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("BANCO DESTINO FAKE");
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("RULE-004: a live detail whose group reference is not in ad_cuentas_bce joins to nothing -> history is read")
    void rule004_liveDetailWithoutCatalogueRowFallsBack() {
        FakeAseSession ase = interbank().interbankDetail(DetailTable.LIVE, ORDER, "999999999", 4, "LIVE-1")
                .interbankDetail(DetailTable.HISTORY, ORDER, GROUP, 4, "HIS-1");

        ase.service().debit(transcli().build());

        assertThat(ase.interbankQueries()).hasSize(2);
        assertThat(ase.onlyEvent().iCtaCre()).isEqualTo("HIS-1");
    }

    @Test
    @DisplayName("RULE-004: detail found nowhere -> institution, credit account and product NULL, the event is still sent and the override applies")
    void rule004_notFoundLeavesNulls() {
        FakeAseSession ase = interbank();

        DebitResult result = ase.service().debit(transcli().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.interbankQueries()).hasSize(2);
        assertThat(ase.onlyEvent().iEmpresa()).isNull();
        assertThat(ase.onlyEvent().iCtaCre()).isNull();
        assertThat(ase.onlyEvent().iProdCre()).isNull();
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("RULE-004 (open question, only the deterministic part): two live rows -> rowcount 2, no history read, the three fields come from ONE of the rows")
    void rule004_severalLiveRowsOnlyDeterministicPartPinned() {
        FakeAseSession ase = interbank()
                .interbankDetail(DetailTable.LIVE, ORDER, GROUP, 3, "ROW-A-111")
                .interbankDetail(DetailTable.LIVE, ORDER, GROUP, 4, "ROW-B-222");
        DebitRequest request = transcli().build();

        NotificationOutcome outcome = new CustomerNotifications(ase, ase, ase, ase, ase).notify(new NotificationContext(
                request, request.iServicio(), 2701, "0150", request.iValorDebito(), new BigDecimal("0.50"), null, 9001));

        assertThat(outcome.wRowdbBiz()).isEqualTo(2);
        assertThat(ase.interbankQueries()).containsExactly(new OrderQuery(DetailTable.LIVE, ORDER));
        assertThat(ase.events()).extracting(EventCommand::iCtaCre, EventCommand::iProdCre)
                .containsAnyOf(tuple("ROW-A-111", "CTE"), tuple("ROW-B-222", "AHO"));
        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("BANCO DESTINO FAKE");
    }

    @Test
    @DisplayName("RULE-005: for a non-interbank service @w_tip_cta is never set, so the 8/9 mapping never fires (credit product NULL)")
    void rule005_creditProductOnlyForInterbank() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICA ROLPAGO").currentOwner(Requests.ACCOUNT, 1)
                .bceInstitution(BCE_NAME).interbankDetail(DetailTable.LIVE, ORDER, GROUP, 9, "2200");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(ase.onlyEvent().iProdCre()).isNull();
        assertThat(ase.onlyEvent().iCtaCre()).isNull();
    }
}
