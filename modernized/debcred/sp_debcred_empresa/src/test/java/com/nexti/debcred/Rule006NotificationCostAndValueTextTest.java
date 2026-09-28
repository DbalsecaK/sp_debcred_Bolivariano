package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * RULE-006 (sp_debcred_empresa.sp:980-986):
 * <pre>
 * select @w_valor_comision = isnull(@w_valor_comision, 0) + isnull(@i_valor2_swift, 0)
 * select @w_valor_sms = convert(varchar(11), @i_valor_debito)
 * select @w_costo_sms = convert(varchar(11), @w_valor_comision)
 * </pre>
 * {@code @w_valor_comision} is not assigned before B7 (NULL): TRANSWIFT sets it to the normalized
 * {@code @i_valor_comision}, TRANSCLI/TARJCRED/COMEXT to the normalized {@code @i_comision}, any other service
 * leaves it NULL (so only the second SWIFT value counts). The texts are ASE money-to-varchar: plain digits,
 * a point and two decimals, no thousands separator; {@code 99999999.99} is the longest value that fits
 * varchar(11). What ASE does with 12+ characters is an open question and is not pinned. Rounding of a
 * 4-decimal money to cents is derived (not recorded): to confirm with the bank's recorded outputs (A6).
 * Fake data only.
 */
class Rule006NotificationCostAndValueTextTest {

    /** SMS rows for the services the cases use, all on channel DIR ('SAT'). */
    private static FakeAseSession configured() {
        return new FakeAseSession()
                .smsService("TSW-SAT", "NOTIFICA TRANSWIFT")
                .smsService("TRC-SAT", "NOTIFICA TRANSCLI")
                .smsService("ROL-SAT", "NOTIFICA ROLPAGO")
                .currentOwner(Requests.ACCOUNT, 700001);
    }

    @ParameterizedTest(name = "RULE-006: {0} comision={1} valorComision={2} valor2Swift={3} -> cost ''{4}''")
    @CsvSource(value = {
            "TRANSWIFT, 0,    12.00, 5.00, 17.00",
            "TRANSWIFT, 0,    NULL,  5.00, 5.00",
            "TRANSWIFT, 0,    12.00, 0,    12.00",
            "TRANSWIFT, 12.00, 0,    0,    12.00",
            "TRANSCLI,  0.50, NULL,  0,    0.50",
            "TRANSCLI,  0.50, 0,     0,    0.00",
            "TRANSCLI,  0.50, NULL,  2.00, 2.50",
            "ROLPAGO,   3.00, 7.00,  0,    0.00",
            "ROLPAGO,   0,    0,     1.25, 1.25",
            "ROLPAGO,   0,    0,     NULL, 0.00"}, nullValues = "NULL")
    void rule006_cost(String service, String comision, String valorComision, String valor2Swift, String cost) {
        // valor2Swift NULL becomes 0 by the parameter default (RULE-037), the same as isnull(@i_valor2_swift, 0)
        FakeAseSession ase = configured();

        ase.service().debit(Requests.currentAccount().servicio(service).canal("DIR").comision(comision)
                .valorComision(valorComision).valor2Swift(valor2Swift).build());

        assertThat(ase.onlyEvent().iCosto()).isEqualTo(cost);
    }

    @Test
    @DisplayName("RULE-006 (brief example): TRANSWIFT, separate commission 12.00, second SWIFT 5.00, debit 1,000.00 -> value '1000.00', cost '17.00'")
    void rule006_briefExample() {
        FakeAseSession ase = configured();

        ase.service().debit(Requests.currentAccount().servicio("TRANSWIFT").codSwift("BICFAKEX").comision("0")
                .valorComision("12.00").valor2Swift("5.00").valorDebito("1000.00").valorOrdenado("1000.00").build());

        assertThat(ase.onlyEvent().iValor()).isEqualTo("1000.00");
        assertThat(ase.onlyEvent().iCosto()).isEqualTo("17.00");
    }

    @ParameterizedTest(name = "RULE-006: debit value {0} -> @w_valor_sms ''{1}''")
    @CsvSource({
            "100,         100.00",
            "0.5,         0.50",
            "0,           0.00",
            "1000.00,     1000.00",
            "1234567.8,   1234567.80",
            "99999999.99, 99999999.99",
            "12.3400,     12.34"})
    void rule006_valueText(String valorDebito, String text) {
        FakeAseSession ase = configured();

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").valorDebito(valorDebito).build());

        assertThat(ase.onlyEvent().iValor()).isEqualTo(text);
        assertThat(ase.onlyEvent().iValor().length()).isLessThanOrEqualTo(11);
    }

    @Test
    @DisplayName("RULE-006 (derived): a 4-decimal money is shown with 2 decimals, rounded (12.3456 -> '12.35'); cost uses the same conversion")
    void rule006_fourDecimalMoneyRoundedToCents() {
        FakeAseSession ase = configured();

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").valorDebito("12.3456").valor2Swift("0.0149").build());

        assertThat(ase.onlyEvent().iValor()).isEqualTo("12.35");
        assertThat(ase.onlyEvent().iCosto()).isEqualTo("0.01");
    }

    @Test
    @DisplayName("RULE-006: a NULL debit value converts to a NULL text (never the word 'null')")
    void rule006_nullValueGivesNullText() {
        FakeAseSession ase = configured();

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").valorDebito(null).build());

        assertThat(ase.onlyEvent().iValor()).isNull();
        assertThat(ase.onlyEvent().iCosto()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("RULE-006 + RULE-003: for TRANSCLI the value text is the ORDERED value (the override happens before the conversion)")
    void rule006_rule003_valueTextAfterOverride() {
        FakeAseSession ase = configured();

        ase.service().debit(Requests.currentAccount().servicio("TRANSCLI").valorDebito("251.20").valorOrdenado("250.00")
                .comision("1.20").valorComision(null).build());

        assertThat(ase.onlyEvent().iValor()).isEqualTo("250.00");
        assertThat(ase.onlyEvent().iCosto()).isEqualTo("1.20");
    }
}
