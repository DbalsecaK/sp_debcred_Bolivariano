package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.Requests;

/**
 * Architecture review Phase 3 H1: a lost ASE transaction (deadlock victim, lost connection) is never
 * treated as "0 rows" by the order-header step, so the debit can never be reported as a success that
 * was not persisted. Statement-level failures keep the approved "0 rows" parity (RULE-014 tests).
 */
class AbortedTransactionTest {

    private static final class LostTransaction implements OrderHeaderRepository {
        @Override
        public int markLiveInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
            throw new AseTransactionAbortedException("deadlock victim (1205)", null);
        }

        @Override
        public int markHistoryInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
            throw new AssertionError("history must not be tried after the transaction was lost");
        }
    }

    @Test
    void rule014_aLostTransactionDuringTheHeaderUpdatePropagatesInsteadOfCountingZeroRows() {
        OrderHeaderContext ctx = new OrderHeaderContext(Requests.currentAccount().build(), "ROLPAGO", "CUE", "S", 0, null);

        assertThatThrownBy(() -> new OrderHeaderTransition(new LostTransaction()).update(ctx))
                .isInstanceOf(AseTransactionAbortedException.class);
    }
}
