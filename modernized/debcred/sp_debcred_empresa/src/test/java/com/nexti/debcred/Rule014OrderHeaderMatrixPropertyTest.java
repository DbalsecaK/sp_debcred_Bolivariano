package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.HeaderRow;
import com.nexti.debcred.support.FakeAseSession.HeaderTable;
import com.nexti.debcred.support.Requests;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * RULE-014 (P0) and RULE-018/RULE-010 over the whole B12 branch matrix (sp_debcred_empresa.sp:1876-2090),
 * property-based: channel (the five direct ones, trailing blanks, leading blank, NULL -> 'DIR' by
 * RULE-037, unknown) x option (01/02/03, trailing blank, '04', NULL) x order/debit payment form (incl.
 * NULL) x random live and history rows (order, form, service, state in I/NULL/T/X/P). The oracle below is
 * an independent transcription of the T-SQL: exactly the expected rows move to 'T' with code 0, history
 * is used only when the live UPDATE hit nothing, and 122004 (full rollback: every row back to its initial
 * state) fires when the final rowcount is 0 for this non-exempt service. Fake data only.
 */
class Rule014OrderHeaderMatrixPropertyTest {

    private static final int ORDER = Requests.BANK_ORDER;
    private static final int OTHER_ORDER = 999001;
    private static final List<String> FORMS = List.of("CUE", "EFE", "CHL", "CHE", "COB", "TRC", "CTB", "CPD", "TPD", "SPI", "CUE ");

    record Row(int order, String form, String service, String state) {
    }

    @Provide
    Arbitrary<String> channels() {
        return Arbitraries.of("DIR", "SFR", "FR2", "BTH", "VEN", "DIR ", "FR2  ", " VEN", "WEB", "ATM", "CAJ").injectNull(0.1);
    }

    @Provide
    Arbitrary<String> options() {
        return Arbitraries.of("01", "02", "03", "01 ", "03  ", "04", "00", " 02").injectNull(0.1);
    }

    @Provide
    Arbitrary<String> forms() {
        return Arbitraries.of("CUE", "EFE", "CHL", "CHE", "COB", "TPD", "SPI").injectNull(0.15);
    }

    @Provide
    Arbitrary<List<Row>> rows() {
        Arbitrary<Row> row = Combinators.combine(
                Arbitraries.of(ORDER, ORDER, ORDER, OTHER_ORDER),
                Arbitraries.of(FORMS),
                Arbitraries.of("ROLPAGO", "ROLPAGO", "ROLPAGO ", "TRANSCLI"),
                Arbitraries.of("I", "I", "T", "X", "P", "I ").injectNull(0.2)).as(Row::new);
        return row.list().ofMinSize(0).ofMaxSize(6);
    }

    @Property(tries = 1000)
    @Label("RULE-014 + RULE-018 + RULE-010: exactly the expected header rows move to 'T' for any channel x option x form x state")
    void rule014_rule018_rule010_exactlyTheExpectedRowsMove(
            @ForAll("channels") String channel,
            @ForAll("options") String option,
            @ForAll("forms") String frmPagcob,
            @ForAll("forms") String frmPagcobDeb,
            @ForAll("rows") List<Row> live,
            @ForAll("rows") List<Row> history) {
        FakeAseSession ase = new FakeAseSession().noHeaderRows();
        live.forEach(r -> ase.headerRow(r.order(), r.form(), r.service(), r.state()));
        history.forEach(r -> ase.historyHeaderRow(r.order(), r.form(), r.service(), r.state()));
        DebitRequest request = Requests.currentAccount().servicio("ROLPAGO").canal(channel).opcion(option)
                .frmPagcob(frmPagcob).frmPagcobDeb(frmPagcobDeb).build();

        DebitResult result = ase.service().debit(request);

        // ---- oracle: a transcription of lines 1876-2090 ----
        String effectiveChannel = channel == null ? "DIR" : channel;                      // RULE-037 default
        String debitForm = frmPagcobDeb != null ? frmPagcobDeb : frmPagcob;                  // line 262
        Set<String> formSet;                                                                 // null = no UPDATE at all
        if (Set.of("DIR", "SFR", "FR2", "BTH", "VEN").contains(rtrim(effectiveChannel))) {
            String opt = option == null ? null : rtrim(option);
            if ("01".equals(opt)) {
                formSet = debitForm == null ? Set.of() : Set.of(rtrim(debitForm));
            } else if ("02".equals(opt)) {
                formSet = Set.of("CUE", "EFE", "CHL");
            } else if ("03".equals(opt)) {
                formSet = Set.of("CUE", "EFE", "CHE");
            } else {
                formSet = null;
            }
        } else {
            formSet = Set.of("COB", "TRC", "CTB", "CPD", "TPD");
        }
        List<HeaderRow> expectedLive = initial(live);
        List<HeaderRow> expectedHistory = initial(history);
        Integer rowCount = null;                                                             // @wRowdbBiz, NULL at line 140
        if (formSet != null) {
            rowCount = move(expectedLive, formSet);
            if (rowCount <= 0) {
                rowCount = move(expectedHistory, formSet);
            }
        }
        boolean error122004 = rowCount != null && rowCount == 0;                             // ROLPAGO, act_totord 'S'

        if (error122004) {
            assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
            assertThat(ase.rollbacks()).isEqualTo(1);
            assertThat(ase.commits()).isZero();
            assertThat(ase.headerRows(HeaderTable.LIVE)).as("rolled back").isEqualTo(initial(live));
            assertThat(ase.headerRows(HeaderTable.HISTORY)).as("rolled back").isEqualTo(initial(history));
        } else {
            assertThat(result).isEqualTo(new DebitResult(0, 0, null));
            assertThat(ase.commits()).isEqualTo(1);
            assertThat(ase.headerRows(HeaderTable.LIVE)).isEqualTo(expectedLive);
            assertThat(ase.headerRows(HeaderTable.HISTORY)).isEqualTo(expectedHistory);
        }
        int expectedUpdates = formSet == null ? 0 : (move(initial(live), formSet) > 0 ? 1 : 2);
        assertThat(ase.headerUpdates()).as("UPDATE statements issued").hasSize(expectedUpdates);
    }

    private static List<HeaderRow> initial(List<Row> rows) {
        List<HeaderRow> out = new ArrayList<>();
        rows.forEach(r -> out.add(new HeaderRow(r.order(), r.form(), r.service(), r.state(), null)));
        return out;
    }

    /** {@code update ... set 'T', 0 where order = 123456 and form in (set) and service = 'ROLPAGO' and isnull(state,'I') = 'I'}. */
    private static int move(List<HeaderRow> rows, Set<String> formSet) {
        int count = 0;
        for (int i = 0; i < rows.size(); i++) {
            HeaderRow r = rows.get(i);
            boolean initialState = r.state() == null || "I".equals(rtrim(r.state()));
            if (r.order() == ORDER && formSet.contains(rtrim(r.form())) && "ROLPAGO".equals(rtrim(r.service())) && initialState) {
                rows.set(i, new HeaderRow(r.order(), r.form(), r.service(), "T", 0));
                count++;
            }
        }
        return count;
    }

    private static String rtrim(String s) {
        return s.replaceAll(" +$", "");
    }
}
