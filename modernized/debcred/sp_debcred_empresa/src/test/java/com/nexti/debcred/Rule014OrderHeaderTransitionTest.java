package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
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
 * RULE-014 (P0), block B12 (sp_debcred_empresa.sp:1876-2070): after a successful debit and commission
 * the order header rows in state 'I' (or NULL) move to 'T' with {@code @w_cod_errord}, for the payment
 * forms implied by channel and option:
 * <ul>
 *   <li>channel {@code = 'DIR'|'SFR'|'FR2'|'BTH'|'VEN'} (Sybase {@code =}: trailing blanks ignored):
 *       option '01' -> {@code in (@i_frm_pagcob_deb)} (= isnull(deb, frm_pagcob), line 262);
 *       '02' -> CUE, EFE, CHL; '03' -> CUE, EFE, CHE; anything else -> no update;</li>
 *   <li>any other channel -> COB, TRC, CTB, CPD, TPD whatever the option.</li>
 * </ul>
 * Only rows of {@code @i_orden} and of the WORKING {@code @i_servicio} move. Fake data only (order
 * 123456, company 500).
 */
class Rule014OrderHeaderTransitionTest {

    private static final int ORDER = Requests.BANK_ORDER;
    private static final int OTHER_ORDER = 999001;

    private static FakeAseSession allForms(String service, String state) {
        FakeAseSession ase = new FakeAseSession();
        for (String form : List.of("CUE", "EFE", "CHL", "CHE", "COB", "TRC", "CTB", "CPD", "TPD", "SPI")) {
            ase.headerRow(ORDER, form, service, state);
        }
        return ase;
    }

    private static List<String> moved(FakeAseSession ase) {
        return ase.headerRows(HeaderTable.LIVE).stream().filter(r -> "T".equals(r.state())).map(r -> r.form()).toList();
    }

    @Test
    @DisplayName("RULE-014: DIR + option '02' moves exactly CUE, EFE, CHL from I to T with error code 0, then commits")
    void rule014_directOption02MovesCueEfeChl() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().canal("DIR").opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(moved(ase)).containsExactly("CUE", "EFE", "CHL");
        assertThat(ase.headerCodError(ORDER, "CUE", "ROLPAGO")).isEqualTo(0);
        assertThat(ase.headerCodError(ORDER, "CHE", "ROLPAGO")).as("untouched row keeps its code").isNull();
        assertThat(ase.headerState(ORDER, "CHE", "ROLPAGO")).isEqualTo("I");
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.headerUpdates()).containsExactly(
                new HeaderUpdate(HeaderTable.LIVE, ORDER, List.of("CUE", "EFE", "CHL"), "ROLPAGO", 0, 3));
    }

    @Test
    @DisplayName("RULE-014: DIR + option '03' moves CUE, EFE, CHE (CHE, not CHL)")
    void rule014_directOption03MovesCueEfeChe() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().opcion("03").build());

        assertThat(moved(ase)).containsExactly("CUE", "EFE", "CHE");
        assertThat(ase.onlyHeaderUpdateForms()).containsExactly("CUE", "EFE", "CHE");
    }

    @Test
    @DisplayName("RULE-014: DIR + option '01' moves only @i_frm_pagcob_deb when given")
    void rule014_directOption01UsesExplicitDebitPaymentForm() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().opcion("01").frmPagcob("CUE").frmPagcobDeb("CHE").build());

        assertThat(moved(ase)).containsExactly("CHE");
        assertThat(ase.onlyHeaderUpdateForms()).containsExactly("CHE");
    }

    @Test
    @DisplayName("RULE-014 + RULE-025: option '01' with no debit form uses @i_frm_pagcob (line 262), never the TRANSQUICK SPI form")
    void rule014_rule025_option01DefaultsToOrderPaymentFormNotTheSpiSwap() {
        FakeAseSession ase = allForms("TRANSQUICK", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("TRANSQUICK").opcion("01")
                .frmPagcob("CUE").frmPagcobSpi("SPI").frmPagcobDeb(null).comision("0").valorComision("1.00").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyCommissionCommand().iFrmPagcob()).as("the commission did swap (RULE-025)").isEqualTo("SPI");
        assertThat(moved(ase)).as("the header did not").containsExactly("CUE");
    }

    @Test
    @DisplayName("RULE-014: option '01' with NULL debit and order forms -> 'in (NULL)' matches nothing, live then history both issued")
    void rule014_option01WithNullFormMatchesNothing() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        DebitResult result = ase.service().debit(Requests.currentAccount().opcion("01").frmPagcob(null).frmPagcobDeb(null).build());

        assertThat(moved(ase)).isEmpty();
        assertThat(ase.headerUpdates()).containsExactly(
                new HeaderUpdate(HeaderTable.LIVE, ORDER, Arrays.asList((String) null), "ROLPAGO", 0, 0),
                new HeaderUpdate(HeaderTable.HISTORY, ORDER, Arrays.asList((String) null), "ROLPAGO", 0, 0));
        assertThat(result).as("RULE-010: nothing updated, ROLPAGO not exempt").isEqualTo(new DebitResult(0, 122004, null));
    }

    @ParameterizedTest(name = "RULE-014: direct channel ''{0}'' + option ''02'' uses CUE/EFE/CHL")
    @ValueSource(strings = {"DIR", "SFR", "FR2", "BTH", "VEN", "DIR ", "VEN   "})
    void rule014_everyDirectChannelIncludingTrailingBlanksUsesTheOptionBranch(String channel) {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().canal(channel).opcion("02").build());

        assertThat(moved(ase)).containsExactly("CUE", "EFE", "CHL");
    }

    @ParameterizedTest(name = "RULE-014: other channel ''{0}'' uses COB/TRC/CTB/CPD/TPD whatever the option")
    @ValueSource(strings = {"WEB", "ATM", " DIR", "DI", "BTHX"})
    void rule014_otherChannelsUseTheCollectionForms(String channel) {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().canal(channel).opcion("04").build());

        assertThat(moved(ase)).containsExactly("COB", "TRC", "CTB", "CPD", "TPD");
        assertThat(ase.onlyHeaderUpdateForms()).containsExactly("COB", "TRC", "CTB", "CPD", "TPD");
    }

    @Test
    @DisplayName("RULE-037 + RULE-014: a NULL channel is defaulted to 'DIR' (compact constructor) and takes the option branch")
    void rule014_rule037_nullChannelBehavesAsDir() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().canal(null).opcion("03").build());

        assertThat(moved(ase)).containsExactly("CUE", "EFE", "CHE");
    }

    @Test
    @DisplayName("RULE-014: option '02 ' (trailing blank) is option '02'")
    void rule014_optionWithTrailingBlankMatches() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().opcion("02 ").build());

        assertThat(moved(ase)).containsExactly("CUE", "EFE", "CHL");
    }

    @Test
    @DisplayName("RULE-014 + RULE-018: a NULL te_estado_proceso is 'I' and moves; T, X and P rows are never touched")
    void rule014_rule018_nullStateMovesOtherStatesUntouched() {
        FakeAseSession ase = new FakeAseSession()
                .headerRow(ORDER, "CUE", "ROLPAGO", null)
                .headerRow(ORDER, "EFE", "ROLPAGO", "T")
                .headerRow(ORDER, "CHL", "ROLPAGO", "X")
                .headerRow(ORDER, "CHL", "ROLPAGO", "P");

        DebitResult result = ase.service().debit(Requests.currentAccount().opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerRows(HeaderTable.LIVE)).containsExactly(
                new FakeAseSession.HeaderRow(ORDER, "CUE", "ROLPAGO", "T", 0),
                new FakeAseSession.HeaderRow(ORDER, "EFE", "ROLPAGO", "T", null),
                new FakeAseSession.HeaderRow(ORDER, "CHL", "ROLPAGO", "X", null),
                new FakeAseSession.HeaderRow(ORDER, "CHL", "ROLPAGO", "P", null));
    }

    @Test
    @DisplayName("RULE-014: rows of other orders and of other services are untouched; only order 123456 / ROLPAGO moves")
    void rule014_onlyMatchingOrderAndServiceMove() {
        FakeAseSession ase = new FakeAseSession()
                .headerRow(OTHER_ORDER, "CUE", "ROLPAGO", "I")
                .headerRow(ORDER, "CUE", "TRANSCLI", "I")
                .headerRow(ORDER, "EFE", "ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").opcion("02").build());

        assertThat(ase.headerState(OTHER_ORDER, "CUE", "ROLPAGO")).isEqualTo("I");
        assertThat(ase.headerState(ORDER, "CUE", "TRANSCLI")).isEqualTo("I");
        assertThat(ase.headerState(ORDER, "EFE", "ROLPAGO")).isEqualTo("T");
    }

    @Test
    @DisplayName("RULE-014: a service or form with trailing blanks in the row still matches (Sybase '=')")
    void rule014_trailingBlanksInRowsMatch() {
        FakeAseSession ase = new FakeAseSession().headerRow(ORDER, "CUE ", "ROLPAGO   ", "I ");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.headerState(ORDER, "CUE ", "ROLPAGO   ")).isEqualTo("T");
    }

    @Test
    @DisplayName("RULE-014: the step writes the context's error code (@w_cod_errord) into te_codigo_error")
    void rule014_stepWritesTheContextErrorCode() {
        FakeAseSession ase = new FakeAseSession().headerRow(ORDER, "CUE", "ROLPAGO", "I");
        DebitRequest request = Requests.currentAccount().opcion("02").build();

        int numError = new OrderHeaderTransition(ase).update(new OrderHeaderContext(request, "ROLPAGO", "CUE", "S", 7, null));

        assertThat(numError).isZero();
        assertThat(ase.headerCodError(ORDER, "CUE", "ROLPAGO")).isEqualTo(7);
        assertThat(ase.headerUpdates()).extracting(HeaderUpdate::codError).containsExactly(7);
    }

    @Test
    @DisplayName("RULE-014: the step uses the context's service and debit form, not the request's")
    void rule014_stepUsesWorkingServiceAndDebitForm() {
        FakeAseSession ase = new FakeAseSession()
                .headerRow(ORDER, "EFE", "TRANSCLI", "I")
                .headerRow(ORDER, "CUE", "SPI", "I");
        DebitRequest request = Requests.currentAccount().servicio("SPI").frmPagcob("CUE").opcion("01").build();

        new OrderHeaderTransition(ase).update(new OrderHeaderContext(request, "TRANSCLI", "EFE", "N", 0, null));

        assertThat(ase.headerState(ORDER, "EFE", "TRANSCLI")).isEqualTo("T");
        assertThat(ase.headerState(ORDER, "CUE", "SPI")).isEqualTo("I");
    }

    @Test
    @DisplayName("RULE-014: the header update runs after the commission and before the commit; the context carries the working values")
    void rule014_headerUpdateRunsBetweenCommissionAndCommit() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().comision("0").valorComision("2.00").build());

        assertThat(ase.calls()).endsWith("sp_grb_mov_y_frmpgo", "commissionStep", "sp_grb_comision", "orderHeaderStep", "commit");
        OrderHeaderContext ctx = ase.onlyOrderHeaderStep();
        assertThat(ctx).isEqualTo(new OrderHeaderContext(ctx.request(), "ROLPAGO", "CUE", "S", 0, null));
    }

    @Test
    @DisplayName("RULE-014 + RULE-020: a ROLPAGO happy path issues one header UPDATE and no other write (the commented bp_orden block 2098-2124 is not migrated)")
    void rule014_rule020_rolpagoIssuesNoOtherWrite() {
        FakeAseSession ase = allForms("ROLPAGO", "I");

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").build());

        assertThat(ase.headerUpdates()).hasSize(1);
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo");
        assertThat(ase.calls()).endsWith("orderHeaderStep", "commit");
    }

    @Test
    @DisplayName("RULE-014 + RULE-012: exit A (debit failed) never reaches the header: the order stays 'I'")
    void rule014_exitANeverUpdatesTheHeader() {
        FakeAseSession ase = allForms("ROLPAGO", "I").debitNote(201045, null);

        ase.service().debit(Requests.currentAccount().build());

        assertThat(ase.headerUpdates()).isEmpty();
        assertThat(moved(ase)).isEmpty();
    }

    @Test
    @DisplayName("RULE-014 + RULE-013 (walkthrough 4): exit B (commission failed) leaves the order in 'I', re-runnable")
    void rule014_rule013_exitBLeavesTheOrderInitial() {
        FakeAseSession ase = allForms("ROLPAGO", "I").commission(0, 122010);

        DebitResult result = ase.service().debit(Requests.currentAccount().comision("0").valorComision("2.00").build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.headerUpdates()).isEmpty();
        assertThat(ase.headerState(ORDER, "CUE", "ROLPAGO")).isEqualTo("I");
    }
}
