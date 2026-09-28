package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.DetailTable;
import com.nexti.debcred.support.FakeAseSession.SwiftQuery;
import com.nexti.debcred.support.Requests;

/**
 * RULE-019 (sp_debcred_empresa.sp:820-874): for TRANSWIFT the notification reads the institution
 * ({@code dt_referencia_grupo}) and the credit account (first 30 characters of {@code dt_nom_cuenta})
 * from {@code bp_orden x bp_detalle} by order, detail sequence ({@code @i_secuencial}) and ordering
 * company ({@code @i_empresa}); when the live read returns no row, the same read runs on
 * {@code db_sat_his}. The notification commission is the normalized {@code @i_valor_comision}. Any other
 * service: institution and credit account NULL. Also A17: {@code @wRowdbBiz} keeps the LIVE rowcount.
 * Fake data only (bank-group reference {@code GRPFAKE01}, account names invented).
 */
class Rule019SwiftNotificationDetailTest {

    private static final int ORDER = Requests.BANK_ORDER;
    private static final int COMPANY = Requests.COMPANY;
    private static final String LONG_NAME = "CUENTA BENEFICIARIO EXTERIOR FAKE 0001";   // 38 characters
    private static final String FIRST_30 = "CUENTA BENEFICIARIO EXTERIOR F";

    private static FakeAseSession swiftSms() {
        return new FakeAseSession().smsService("TSW-SAT", "NOTIFICA TRANSWIFT EXTERIOR").currentOwner(Requests.ACCOUNT, 700001);
    }

    private static Requests swift() {
        return Requests.currentAccount().servicio("TRANSWIFT").codSwift("BICFAKEX").secuencial(2).empresa(COMPANY)
                .canal("DIR").comision("0").valorComision("12.00").valor2Swift("0");
    }

    @Test
    @DisplayName("RULE-019: live detail found by order, sequence and ordering company -> institution = dt_referencia_grupo, credit account = first 30 chars; history not read")
    void rule019_liveDetail() {
        FakeAseSession ase = swiftSms().swiftDetail(DetailTable.LIVE, ORDER, 2, COMPANY, "GRPFAKE01", LONG_NAME);

        DebitResult result = ase.service().debit(swift().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.swiftQueries()).containsExactly(new SwiftQuery(DetailTable.LIVE, ORDER, 2, COMPANY));
        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("GRPFAKE01");
        assertThat(ase.onlyEvent().iCtaCre()).isEqualTo(FIRST_30).hasSize(30);
        assertThat(ase.onlyEvent().iProdCre()).as("@w_tip_cta is never set for TRANSWIFT").isNull();
        assertThat(ase.interbankQueries()).as("TRANSWIFT is not an interbank service").isEmpty();
    }

    @Test
    @DisplayName("RULE-019 + RULE-024: detail only in db_sat_his -> the same keys are read there and its values are used")
    void rule019_historyFallback() {
        FakeAseSession ase = swiftSms().swiftDetail(DetailTable.HISTORY, ORDER, 2, COMPANY, "GRPHIS002", "CTA HISTORICA");

        ase.service().debit(swift().build());

        assertThat(ase.swiftQueries()).containsExactly(
                new SwiftQuery(DetailTable.LIVE, ORDER, 2, COMPANY),
                new SwiftQuery(DetailTable.HISTORY, ORDER, 2, COMPANY));
        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("GRPHIS002");
        assertThat(ase.onlyEvent().iCtaCre()).isEqualTo("CTA HISTORICA");
    }

    @Test
    @DisplayName("RULE-019: a live row for another sequence or another ordering company does not match; history is read")
    void rule019_keysIncludeSequenceAndOrderingCompany() {
        FakeAseSession ase = swiftSms()
                .swiftDetail(DetailTable.LIVE, ORDER, 1, COMPANY, "OTRASEC01", "OTRA SECUENCIA")
                .swiftDetail(DetailTable.LIVE, ORDER, 2, 501, "OTRAEMP01", "OTRA EMPRESA")
                .swiftDetail(DetailTable.HISTORY, ORDER, 2, COMPANY, "GRPHIS002", "CTA HISTORICA");

        ase.service().debit(swift().build());

        assertThat(ase.swiftQueries()).extracting(SwiftQuery::table).containsExactly(DetailTable.LIVE, DetailTable.HISTORY);
        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("GRPHIS002");
    }

    @Test
    @DisplayName("RULE-019: detail found nowhere -> institution and credit account stay NULL and the notification is still sent")
    void rule019_notFoundLeavesNulls() {
        FakeAseSession ase = swiftSms();

        DebitResult result = ase.service().debit(swift().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.swiftQueries()).hasSize(2);
        assertThat(ase.onlyEvent().iEmpresa()).isNull();
        assertThat(ase.onlyEvent().iCtaCre()).isNull();
    }

    @Test
    @DisplayName("RULE-019: default @i_secuencial 0 is the detail key when the caller omits it (RULE-037)")
    void rule019_defaultSequenceZero() {
        FakeAseSession ase = swiftSms().swiftDetail(DetailTable.LIVE, ORDER, 0, COMPANY, "GRPFAKE00", "CTA CERO");

        ase.service().debit(swift().secuencial(null).build());

        assertThat(ase.swiftQueries()).containsExactly(new SwiftQuery(DetailTable.LIVE, ORDER, 0, COMPANY));
        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("GRPFAKE00");
    }

    @Test
    @DisplayName("RULE-019: a short account name is kept whole; a NULL name gives a NULL credit account")
    void rule019_shortAndNullAccountName() {
        FakeAseSession shortName = swiftSms().swiftDetail(DetailTable.LIVE, ORDER, 2, COMPANY, "GRPFAKE01", "CTA 12");
        FakeAseSession nullName = swiftSms().swiftDetail(DetailTable.LIVE, ORDER, 2, COMPANY, "GRPFAKE01", null);

        shortName.service().debit(swift().build());
        nullName.service().debit(swift().build());

        assertThat(shortName.onlyEvent().iCtaCre()).isEqualTo("CTA 12");
        assertThat(nullName.onlyEvent().iCtaCre()).isNull();
        assertThat(nullName.onlyEvent().iEmpresa()).isEqualTo("GRPFAKE01");
    }

    @Test
    @DisplayName("RULE-019: the service test is trimmed (' TRANSWIFT ' reads the SWIFT detail)")
    void rule019_paddedServiceIsTranswift() {
        FakeAseSession ase = swiftSms().swiftDetail(DetailTable.LIVE, ORDER, 2, COMPANY, "GRPFAKE01", "CTA");

        ase.service().debit(swift().servicio(" TRANSWIFT ").build());

        assertThat(ase.swiftQueries()).hasSize(1);
        assertThat(ase.onlyEvent().iEmpresa()).isEqualTo("GRPFAKE01");
    }

    @Test
    @DisplayName("RULE-019: any other service -> no SWIFT read, institution and credit account NULL")
    void rule019_otherServiceHasNoInstitution() {
        FakeAseSession ase = new FakeAseSession().smsService("ROL-SAT", "NOTIFICACION ROLPAGO").currentOwner(Requests.ACCOUNT, 1)
                .swiftDetail(DetailTable.LIVE, ORDER, 0, COMPANY, "GRPFAKE01", "CTA");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(ase.swiftQueries()).isEmpty();
        assertThat(ase.onlyEvent().iEmpresa()).isNull();
        assertThat(ase.onlyEvent().iCtaCre()).isNull();
    }

    @Test
    @DisplayName("RULE-019 + RULE-006: notification commission = the NORMALIZED @i_valor_comision (bundled 12.00 with separate 0 moves over)")
    void rule019_rule006_commissionIsNormalizedSeparateCommission() {
        FakeAseSession bundled = swiftSms();
        FakeAseSession both = swiftSms();

        bundled.service().debit(swift().comision("12.00").valorComision("0").build());   // RULE-027: 12.00 -> separate
        both.service().debit(swift().comision("3.00").valorComision("12.00").build());   // no normalization: 3.00 ignored

        assertThat(bundled.onlyEvent().iCosto()).isEqualTo("12.00");
        assertThat(both.onlyEvent().iCosto()).isEqualTo("12.00");
    }

    @Test
    @DisplayName("RULE-019 + A17: step outcome carries the LIVE rowcount: 1 when found live, 0 when found only in history")
    void rule019_a17_outcomeCarriesLiveRowCount() {
        FakeAseSession live = swiftSms().swiftDetail(DetailTable.LIVE, ORDER, 2, COMPANY, "GRPFAKE01", "CTA");
        FakeAseSession history = swiftSms().swiftDetail(DetailTable.HISTORY, ORDER, 2, COMPANY, "GRPFAKE01", "CTA");
        DebitRequest request = swift().build();
        NotificationContext ctx = new NotificationContext(request, request.iServicio(), 2701, "0150",
                request.iValorDebito(), BigDecimal.ZERO, new BigDecimal("12.00"), 9001);

        NotificationOutcome fromLive = new CustomerNotifications(live, live, live, live, live).notify(ctx);
        NotificationOutcome fromHistory = new CustomerNotifications(history, history, history, history, history).notify(ctx);

        assertThat(fromLive).isEqualTo(new NotificationOutcome(true, 0, null, 0, 1));
        assertThat(fromHistory).isEqualTo(new NotificationOutcome(true, 0, null, 0, 0));
    }

    @Test
    @DisplayName("RULE-019 + RULE-036: a 'B' TRANSWIFT notification carries the SWIFT credit account and institution, no credit product")
    void rule019_rule036_basicNotificationCarriesSwiftDetail() {
        FakeAseSession ase = swiftSms().notificationClassRow("TRANSWIFT", "B")
                .swiftDetail(DetailTable.LIVE, ORDER, 2, COMPANY, "GRPFAKE01", LONG_NAME);

        ase.service().debit(swift().build());

        BasicNotificationCommand sent = ase.onlyBasicNotification();
        assertThat(sent.iCtacred()).isEqualTo(FIRST_30);
        assertThat(sent.iEmpresa()).isEqualTo("GRPFAKE01");
        assertThat(sent.iProdCre()).isNull();
        assertThat(ase.events()).isEmpty();
    }
}
