package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.HeaderTable;
import com.nexti.debcred.support.FakeAseSession.HeaderUpdate;
import com.nexti.debcred.support.Requests;

/**
 * RULE-018 (sp_debcred_empresa.sp:1898-1924 and the three sibling blocks): {@code set @wRowdbBiz =
 * @@rowcount}; {@code if @wRowdbBiz <= 0} the same UPDATE runs on {@code db_sat_his..bp_total_orden_his}
 * and {@code @wRowdbBiz} takes ITS rowcount. The history table is never touched when the live update
 * moved a row. A NULL state is 'I' in both tables. Fake data only.
 */
class Rule018HistoryFallbackTest {

    private static final int ORDER = Requests.BANK_ORDER;

    @Test
    @DisplayName("RULE-018: order archived with a NULL state -> live 0 rows, history row moves to 'T' with code 0, no 122004, commit")
    void rule018_archivedOrderIsUpdatedInHistory() {
        FakeAseSession ase = new FakeAseSession().historyHeaderRow(ORDER, "CUE", "ROLPAGO", null);

        DebitResult result = ase.service().debit(Requests.currentAccount().opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.historyHeaderState(ORDER, "CUE", "ROLPAGO")).isEqualTo("T");
        assertThat(ase.historyHeaderCodError(ORDER, "CUE", "ROLPAGO")).isEqualTo(0);
        assertThat(ase.headerUpdates()).containsExactly(
                new HeaderUpdate(HeaderTable.LIVE, ORDER, List.of("CUE", "EFE", "CHL"), "ROLPAGO", 0, 0),
                new HeaderUpdate(HeaderTable.HISTORY, ORDER, List.of("CUE", "EFE", "CHL"), "ROLPAGO", 0, 1));
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.rollbacks()).isZero();
    }

    @Test
    @DisplayName("RULE-018: live update moved a row -> history is never touched, even with an 'I' row there")
    void rule018_liveHitNeverTouchesHistory() {
        FakeAseSession ase = new FakeAseSession()
                .headerRow(ORDER, "CUE", "ROLPAGO", "I")
                .historyHeaderRow(ORDER, "EFE", "ROLPAGO", "I")
                .headerUpdateThrows(HeaderTable.HISTORY);   // would explode if it were reached

        DebitResult result = ase.service().debit(Requests.currentAccount().opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::table).containsExactly(HeaderTable.LIVE);
        assertThat(ase.historyHeaderState(ORDER, "EFE", "ROLPAGO")).isEqualTo("I");
    }

    @Test
    @DisplayName("RULE-018: live rows exist but none is in state I/NULL -> 0 rows -> history is tried")
    void rule018_liveRowsAlreadyInTransitionFallBackToHistory() {
        FakeAseSession ase = new FakeAseSession()
                .headerRow(ORDER, "CUE", "ROLPAGO", "T")
                .historyHeaderRow(ORDER, "CUE", "ROLPAGO", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerState(ORDER, "CUE", "ROLPAGO")).isEqualTo("T");
        assertThat(ase.historyHeaderState(ORDER, "CUE", "ROLPAGO")).isEqualTo("T");
    }

    @Test
    @DisplayName("RULE-018 + RULE-010: a history row not in I/NULL is untouched and the final rowcount 0 raises 122004")
    void rule018_rule010_historyRowNotInitialGives122004() {
        FakeAseSession ase = new FakeAseSession().historyHeaderRow(ORDER, "CUE", "ROLPAGO", "X");

        DebitResult result = ase.service().debit(Requests.currentAccount().opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.historyHeaderState(ORDER, "CUE", "ROLPAGO")).isEqualTo("X");
    }

    @ParameterizedTest(name = "RULE-018: option ''{0}'' uses the same form set on the history table")
    @ValueSource(strings = {"01", "02", "03"})
    void rule018_historyUpdateRepeatsTheLiveWhereClause(String option) {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();

        ase.service().debit(Requests.currentAccount().opcion(option).frmPagcobDeb("EFE").build());

        List<HeaderUpdate> updates = ase.headerUpdates();
        assertThat(updates).hasSize(2);
        HeaderUpdate live = updates.get(0);
        HeaderUpdate history = updates.get(1);
        assertThat(history.table()).isEqualTo(HeaderTable.HISTORY);
        assertThat(history).isEqualTo(new HeaderUpdate(HeaderTable.HISTORY, live.ordenBanco(), live.paymentForms(),
                live.servicio(), live.codError(), 0));
    }

    @Test
    @DisplayName("RULE-018: other channels fall back to history too (COB/TRC/CTB/CPD/TPD block, 2046-2066)")
    void rule018_otherChannelFallsBackToHistory() {
        FakeAseSession ase = new FakeAseSession().historyHeaderRow(ORDER, "TPD", "ROLPAGO", null);

        DebitResult result = ase.service().debit(Requests.currentAccount().canal("WEB").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.historyHeaderState(ORDER, "TPD", "ROLPAGO")).isEqualTo("T");
    }

    @Test
    @DisplayName("RULE-018: history rows of other orders and services are untouched")
    void rule018_historyOnlyMovesMatchingRows() {
        FakeAseSession ase = new FakeAseSession()
                .historyHeaderRow(ORDER, "CUE", "ROLPAGO", "I")
                .historyHeaderRow(999001, "CUE", "ROLPAGO", "I")
                .historyHeaderRow(ORDER, "CUE", "TRANSCLI", "I");

        ase.service().debit(Requests.currentAccount().opcion("02").build());

        assertThat(ase.historyHeaderState(ORDER, "CUE", "ROLPAGO")).isEqualTo("T");
        assertThat(ase.historyHeaderState(999001, "CUE", "ROLPAGO")).isEqualTo("I");
        assertThat(ase.historyHeaderState(ORDER, "CUE", "TRANSCLI")).isEqualTo("I");
    }
}
