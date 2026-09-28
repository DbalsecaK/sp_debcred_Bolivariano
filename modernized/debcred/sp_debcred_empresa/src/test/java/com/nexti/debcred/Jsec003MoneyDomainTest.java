package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.Requests;

/**
 * Harden JSEC-003 / brief section 7 A21: every money input of the 45 parameters must fit an ASE
 * {@code money} value (+/-922,337,203,685,477.5807). A value outside it could never reach the legacy
 * procedure; here it is refused before anything runs, and cheaply (an 11-character JSON number such as
 * {@code 1E999999999} must not cost a billion-digit division). More than 38 decimals is refused too: no
 * SQL numeric type holds it.
 */
class Jsec003MoneyDomainTest {

    private static final String MAX = "922337203685477.5807";

    @Test
    void theMoneyBoundsThemselvesAreAccepted() {
        DebitRequest request = Requests.currentAccount().valorDebito(MAX).valorOrdenado("-" + MAX).build();

        assertThat(request.iValorDebito()).isEqualByComparingTo(MAX);
        assertThat(request.iValorOrdenado()).isEqualByComparingTo("-" + MAX);
    }

    @ParameterizedTest
    @ValueSource(strings = {"922337203685477.5808", "-922337203685477.5808", "1E999999999", "-1E20"})
    void anAmountOutsideTheMoneyRangeIsRefused(String amount) {
        assertThatThrownBy(() -> Requests.currentAccount().valorDebito(amount).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iValorDebito");
    }

    @Test
    void everyMoneyParameterIsChecked() {
        BigDecimal huge = new BigDecimal("1E30");
        DebitRequest ok = Requests.currentAccount().build();
        String[] names = {"iValorOrdenado", "iValorDebito", "iComision", "iValorComision", "iValor2Swift", "iValorTarifa",
                "iValorComisionCue", "iValorTarifaEfe", "iValorComisionEfe", "iValorTarifaChe", "iValorComisionChe"};
        for (int i = 0; i < names.length; i++) {
            int slot = i;
            assertThatThrownBy(() -> withMoney(ok, slot, huge)).as(names[i])
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(names[i]);
        }
    }

    @Test
    void thirtyEightDecimalsAreAcceptedAndThirtyNineRefused() {
        String thirtyEight = "0." + "0".repeat(37) + "1";
        assertThat(Requests.currentAccount().valorDebito(thirtyEight).build().iValorDebito()).isPositive();

        assertThatThrownBy(() -> Requests.currentAccount().valorDebito("1E-999999999").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRefusalIsCheapAndDoesNotEchoTheValue() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> Requests.currentAccount().valorDebito("1E999999999").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("999999999");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void nullAmountsStayNull() {
        assertThat(Requests.currentAccount().valorOrdenado(null).build().iValorOrdenado()).isNull();
    }

    /** A copy of {@code r} with the {@code slot}-th money component (procedure order) set to {@code value}. */
    private static DebitRequest withMoney(DebitRequest r, int slot, BigDecimal value) {
        BigDecimal[] m = {r.iValorOrdenado(), r.iValorDebito(), r.iComision(), r.iValorComision(), r.iValor2Swift(),
                r.iValorTarifa(), r.iValorComisionCue(), r.iValorTarifaEfe(), r.iValorComisionEfe(), r.iValorTarifaChe(),
                r.iValorComisionChe()};
        m[slot] = value;
        return new DebitRequest(r.sSsn(), r.sUser(), r.sTerm(), r.sSrv(), r.sOfi(), r.iAplcobis(), r.iSpName(), r.iOrden(),
                r.iOrdenEmpresa(), r.iFechaProceso(), r.iNemEmp(), r.iEmpresa(), r.iProducto(), r.iServicio(), r.iFrmPagcob(),
                r.iFrmPagcobSpi(), r.iCanal(), r.iTipoAfec(), r.iReferencia(), r.iMonDebito(), r.iPaisCta(),
                r.iCodBancoCta(), r.iTipctaEmp(), r.iNumctaEmp(), m[0], m[1], m[2], m[3], r.iTipoReferencia(),
                r.iTarjeta(), r.iRefProv(), r.iOpcion(), r.iTipoPagcob(), r.iLocalidadOrden(), r.iTipoProceso(),
                r.iFrmPagcobDeb(), m[4], r.iSecuencial(), r.iCodSwift(), m[5], m[6], m[7], m[8], m[9], m[10]);
    }
}
