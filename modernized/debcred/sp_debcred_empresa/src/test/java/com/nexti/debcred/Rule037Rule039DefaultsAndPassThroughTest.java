package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B0 (lines 4-96): parameter defaults (RULE-037) and the REF33 tariff/commission breakdown
 * that is forwarded untouched to the separate-commission step and never to the main debit note
 * (RULE-039).
 */
class Rule037Rule039DefaultsAndPassThroughTest {

    @Test
    @DisplayName("RULE-037: null aplcobis/canal/tipo_afec/valor2_swift/secuencial -> 'N', 'DIR', '10', 0, 0")
    void rule037_requestDefaultsApplied() {
        DebitRequest request = Requests.currentAccount().aplcobis(null).canal(null).tipoAfec(null).valor2Swift(null)
                .secuencial(null).build();

        assertThat(request.iAplcobis()).isEqualTo("N");
        assertThat(request.iCanal()).isEqualTo("DIR");
        assertThat(request.iTipoAfec()).isEqualTo("10");
        assertThat(request.iValor2Swift()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(request.iSecuencial()).isEqualTo(0);
    }

    @Test
    @DisplayName("RULE-037: nullable parameters without a legacy default stay null")
    void rule037_nullableParametersWithoutDefaultStayNull() {
        DebitRequest request = Requests.currentAccount().sSsn(null).sTerm(null).frmPagcobSpi(null).tarjeta(null)
                .codSwift(null).frmPagcobDeb(null).valorTarifa(null).build();

        assertThat(request.sSsn()).isNull();
        assertThat(request.sTerm()).isNull();
        assertThat(request.iFrmPagcobSpi()).isNull();
        assertThat(request.iTarjeta()).isNull();
        assertThat(request.iCodSwift()).isNull();
        assertThat(request.iFrmPagcobDeb()).isNull();
        assertThat(request.iValorTarifa()).isNull();
    }

    @Test
    @DisplayName("RULE-037: defaulted call on a virtual account -> canal 'DIR' in the lookup and the movement, tipo_afec '10', batch 1 on the virtual debit note")
    void rule037_defaultsDriveLookupMovementAndBatchFlag() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().tipctaEmp(12).aplcobis(null).canal(null).tipoAfec(null).build();

        ase.service().debit(request);

        assertThat(ase.onlyAccountingQuery().canal()).isEqualTo("DIR");
        assertThat(ase.onlyAccountingQuery().tipoafec()).isEqualTo("10");
        assertThat(ase.onlyTariffQuery().codCanal()).isEqualTo("DIR");
        assertThat(ase.onlyTariffQuery().aplcobis()).isEqualTo("N");
        assertThat(ase.onlyVirtualDebitNote().iBatch()).isEqualTo(Boolean.TRUE);
        assertThat(ase.onlyMovement().iCanal()).isEqualTo("DIR");
    }

    @Test
    @DisplayName("RULE-039: the six REF33 values reach the commission step unchanged (request pass-through)")
    void rule039_ref33BreakdownIsForwardedUnchangedToCommissionStep() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().valorComision("2.00")
                .valorTarifa("1.00").valorComisionCue("0.10").valorTarifaEfe("1.50").valorComisionEfe("0.20")
                .valorTarifaChe("1.75").valorComisionChe("0.30").build();

        ase.service().debit(request);

        DebitRequest forwarded = ase.onlyCommissionStep().request();
        assertThat(forwarded.iValorTarifa()).isEqualByComparingTo("1.00");
        assertThat(forwarded.iValorComisionCue()).isEqualByComparingTo("0.10");
        assertThat(forwarded.iValorTarifaEfe()).isEqualByComparingTo("1.50");
        assertThat(forwarded.iValorComisionEfe()).isEqualByComparingTo("0.20");
        assertThat(forwarded.iValorTarifaChe()).isEqualByComparingTo("1.75");
        assertThat(forwarded.iValorComisionChe()).isEqualByComparingTo("0.30");
        assertThat(ase.onlyCommissionStep().valorComision()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("RULE-039: the REF33 values never influence the main debit note or the movement amounts")
    void rule039_ref33BreakdownDoesNotChangeDebitAmounts() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().valorDebito("100.00").valorComision("2.00")
                .valorTarifa("9.00").valorComisionCue("9.10").valorTarifaEfe("9.50").valorComisionEfe("9.20")
                .valorTarifaChe("9.75").valorComisionChe("9.30").build();

        ase.service().debit(request);

        DebitNoteCommand note = ase.onlyDebitNote();
        assertThat(note.iValor()).isEqualByComparingTo("100.00");
        assertThat(note.iNchq()).isEqualTo(4); // 2.00 / 0.50, the breakdown is not involved
        assertThat(note.iSolca()).isEqualByComparingTo("0.50");
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
        assertThat(ase.onlyMovement().iValorComision()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RULE-039: null REF33 values are forwarded as null (defaults are null)")
    void rule039_nullBreakdownForwardedAsNull() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().build());

        DebitRequest forwarded = ase.onlyCommissionStep().request();
        assertThat(forwarded.iValorTarifa()).isNull();
        assertThat(forwarded.iValorComisionCue()).isNull();
        assertThat(forwarded.iValorTarifaEfe()).isNull();
        assertThat(forwarded.iValorComisionEfe()).isNull();
        assertThat(forwarded.iValorTarifaChe()).isNull();
        assertThat(forwarded.iValorComisionChe()).isNull();
    }
}
