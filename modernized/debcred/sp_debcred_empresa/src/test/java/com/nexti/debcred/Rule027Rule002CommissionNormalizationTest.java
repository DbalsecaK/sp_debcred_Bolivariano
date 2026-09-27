package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B1 (lines 168-248): commission normalization (RULE-027) and the per-transaction tariff
 * lookup that yields the unit commission and the transaction count (RULE-002, confirmed as-is
 * in RULE_REVIEWS.json: the truncation and the unconditional overwrite at line 248 are behavior
 * to preserve). Observed through what sp_ndc_ahcc (nchq, solca, tcomision) and
 * sp_grb_mov_y_frmpgo (valor_comision) receive, and through the CommissionStep context.
 */
class Rule027Rule002CommissionNormalizationTest {

    @Test
    @DisplayName("RULE-027: bundled 2.50 with separate 0 -> separate 2.50 and bundled 0 (lines 188-196)")
    void rule027_bundledCommissionMovesToSeparateWhenSeparateIsExactlyZero() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().comision("2.50").valorComision("0").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        // the bundled commission reaching sp_ndc_ahcc @i_tcomision is now 0
        assertThat(ase.onlyDebitNote().iTcomision()).isEqualByComparingTo(BigDecimal.ZERO);
        // and the movement @i_valor_comision (= @i_comision, line 1458) is 0 too
        assertThat(ase.onlyMovement().iValorComision()).isEqualByComparingTo(BigDecimal.ZERO);
        // the separate commission handed to Phase 2 is 2.50
        CommissionContext commission = ase.onlyCommissionStep();
        assertThat(commission.valorComision()).isEqualByComparingTo("2.50");
        assertThat(commission.comision()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RULE-027: separate NULL does not trigger the swap; commission stays bundled")
    void rule027_nullSeparateCommissionKeepsBundledCommission() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().comision("2.50").valorComision(null).build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote().iTcomision()).isEqualByComparingTo("2.50");
        assertThat(ase.onlyMovement().iValorComision()).isEqualByComparingTo("2.50");
        CommissionContext commission = ase.onlyCommissionStep();
        assertThat(commission.valorComision()).isNull();
        assertThat(commission.comision()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("RULE-027: both > 0 -> no swap, both coexist")
    void rule027_bothPositiveNoSwap() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().comision("1.00").valorComision("3.00").build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote().iTcomision()).isEqualByComparingTo("1.00");
        CommissionContext commission = ase.onlyCommissionStep();
        assertThat(commission.comision()).isEqualByComparingTo("1.00");
        assertThat(commission.valorComision()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("RULE-002: tariff query uses alcance 'E', tipo '01', secuencia 1, the request channel and aplcobis (lines 204-222)")
    void rule002_tariffQueryArguments() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().empresa(500).producto(1).servicio("TRANSCLI")
                .canal("BNK").aplcobis("S").build();

        ase.service().debit(request);

        assertThat(ase.onlyTariffQuery())
                .isEqualTo(new CommissionTariffQuery(500, 1, "TRANSCLI", "BNK", "E", "01", 1, "S"));
        // the tariff lookup happens before the accounting lookup and before begin tran
        assertThat(ase.calls()).containsSubsequence("sp_con_comision", "sp_con_confcontable", "begin");
    }

    @Test
    @DisplayName("RULE-002: 2.50 / 0.50 -> nchq 5 and solca 0.50 on sp_ndc_ahcc (lines 242-248, 726-728)")
    void rule002_quantityIsCommissionDividedByTariffAndUnitIsTariff() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().comision("2.50").valorComision("0").build();

        ase.service().debit(request);

        DebitNoteCommand note = ase.onlyDebitNote();
        assertThat(note.iNchq()).isEqualTo(5);
        assertThat(note.iSolca()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("RULE-002: 2.75 / 0.50 = 5.5 is truncated to 5 (int assignment, line 244)")
    void rule002_quantityIsTruncatedNotRounded() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().valorComision("2.75").build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote().iNchq()).isEqualTo(5);
    }

    @Test
    @DisplayName("RULE-002: 1.00 / 0.30 = 3.3333 -> 3")
    void rule002_quantityTruncationRepeatingDecimal() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.30");
        DebitRequest request = Requests.currentAccount().valorComision("1.00").build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote().iNchq()).isEqualTo(3);
        assertThat(ase.onlyDebitNote().iSolca()).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("RULE-002: separate commission is the numerator when both are > 0 (line 236 wins over 230)")
    void rule002_separateCommissionWinsAsNumerator() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().comision("1.00").valorComision("3.00").build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote().iNchq()).isEqualTo(6); // 3.00 / 0.50, not 1.00 / 0.50
    }

    @Test
    @DisplayName("RULE-002: tariff lookup failure -> tariff 0, nchq 0 and solca 0 even though 2.50 was charged (line 226, 248)")
    void rule002_lookupFailureGivesZeroQuantityAndZeroUnitCommission() {
        FakeAseSession ase = new FakeAseSession().tariff(1, "9.99"); // non-zero return code: value must be ignored
        DebitRequest request = Requests.currentAccount().valorComision("2.50").build();

        ase.service().debit(request);

        DebitNoteCommand note = ase.onlyDebitNote();
        assertThat(note.iNchq()).isEqualTo(0);
        assertThat(note.iSolca()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RULE-002: configured tariff 0 -> no division, nchq 0, solca 0")
    void rule002_zeroTariffAvoidsDivision() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0");
        DebitRequest request = Requests.currentAccount().valorComision("2.50").build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote().iNchq()).isEqualTo(0);
        assertThat(ase.onlyDebitNote().iSolca()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RULE-002: no commission at all but tariff 0.50 configured -> nchq 0 and solca 0.50 (line 248 overwrite preserved)")
    void rule002_unitCommissionEqualsTariffEvenWithoutCommission() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().comision(null).valorComision(null).build();

        ase.service().debit(request);

        DebitNoteCommand note = ase.onlyDebitNote();
        assertThat(note.iNchq()).isEqualTo(0);
        assertThat(note.iSolca()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("RULE-002: 0.49 / 0.50 -> 0 (commission below one tariff unit)")
    void rule002_commissionBelowOneUnitGivesZero() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");
        DebitRequest request = Requests.currentAccount().valorComision("0.49").build();

        ase.service().debit(request);

        assertThat(ase.onlyDebitNote().iNchq()).isEqualTo(0);
    }
}
