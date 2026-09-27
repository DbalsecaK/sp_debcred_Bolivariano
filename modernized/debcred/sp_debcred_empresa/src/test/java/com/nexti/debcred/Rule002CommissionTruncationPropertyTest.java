package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * RULE-002 (lines 242-248), property-based: for any commission amount in cents and any configured
 * per-transaction tariff in cents, the transaction count sent to sp_ndc_ahcc as @i_nchq is the
 * integer part of amount / tariff (truncation toward zero, never rounding) and @i_solca is the
 * tariff itself.
 *
 * <p>Oracle: exact integer division of the two amounts expressed in cents. The Sybase expression is
 * a money / money division (4 decimals) assigned to an int. With tariffs of at most 100.00 the
 * quotient's fractional remainder is at least 1/10000, so the 4-decimal money rounding can never
 * lift the result across an integer and the exact integer division is the same oracle.
 */
class Rule002CommissionTruncationPropertyTest {

    @Provide
    Arbitrary<Long> commissionCents() {
        return Arbitraries.longs().between(0L, 1_000_000L); // 0.00 .. 10,000.00
    }

    @Provide
    Arbitrary<Long> tariffCents() {
        return Arbitraries.longs().between(1L, 10_000L); // 0.01 .. 100.00
    }

    @Property(tries = 300)
    @Label("RULE-002: nchq = trunc(commission / tariff) and solca = tariff for random amounts")
    void rule002_quantityIsTruncatedQuotientForAnyAmountAndTariff(
            @ForAll("commissionCents") long commissionCents,
            @ForAll("tariffCents") long tariffCents) {
        BigDecimal commission = BigDecimal.valueOf(commissionCents, 2);
        BigDecimal tariff = BigDecimal.valueOf(tariffCents, 2);
        FakeAseSession ase = new FakeAseSession().tariff(0, tariff.toPlainString());
        DebitRequest request = Requests.currentAccount().valorComision(commission.toPlainString()).build();

        ase.service().debit(request);

        int expected = BigInteger.valueOf(commissionCents).divide(BigInteger.valueOf(tariffCents)).intValueExact();
        DebitNoteCommand note = ase.onlyDebitNote();
        assertThat(note.iNchq()).as("trunc(%s / %s)", commission, tariff).isEqualTo(expected);
        assertThat(note.iSolca()).isEqualByComparingTo(tariff);
    }

    @Property(tries = 100)
    @Label("RULE-002: a failed tariff lookup always yields nchq 0 and solca 0 whatever the commission")
    void rule002_failedLookupAlwaysZero(@ForAll("commissionCents") long commissionCents) {
        BigDecimal commission = BigDecimal.valueOf(commissionCents, 2);
        FakeAseSession ase = new FakeAseSession().tariff(3, "0.75");
        DebitRequest request = Requests.currentAccount().valorComision(commission.toPlainString()).build();

        ase.service().debit(request);

        DebitNoteCommand note = ase.onlyDebitNote();
        assertThat(note.iNchq()).isEqualTo(0);
        assertThat(note.iSolca()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
