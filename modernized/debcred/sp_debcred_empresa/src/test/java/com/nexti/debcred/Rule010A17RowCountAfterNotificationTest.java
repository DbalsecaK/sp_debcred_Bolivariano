package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.DetailTable;
import com.nexti.debcred.support.Requests;

/**
 * Brief section 7 A17 (approved, preserved as-is) with RULE-010 and RULE-014: {@code @wRowdbBiz} is set by
 * the B7 reads from the LIVE lookup only ({@code set @wRowdbBiz = 0}; select; {@code set @wRowdbBiz = @@rowcount},
 * lines 824/840 and 890/912), and it is NOT reassigned after the history fallback. Line 1358 (SPI return)
 * sets it again from the live {@code bp_orden} lookup. B12 runs no UPDATE for a direct channel with an option
 * outside 01-03, so the value B7 left decides {@code if @wRowdbBiz = 0 ... 122004} (2076-2090).
 *
 * <p>Consequence pinned here: TRANSCLI/TARJCRED/COMEXT with the detail only in history (or nowhere), a direct
 * channel and option '04' raise 122004 and the whole debit (notification included) is rolled back; with the
 * detail live they commit. TRANSWIFT gets the same rowcount 0 but is exempt from 122004. The value reaches
 * B12 as {@code OrderHeaderContext.priorRowCount}. Fake data only.
 */
class Rule010A17RowCountAfterNotificationTest {

    private static final int ORDER = Requests.BANK_ORDER;
    private static final String GROUP = "000000017";

    private static FakeAseSession interbank() {
        return new FakeAseSession()
                .smsService("TRC-SAT", "NOTIFICA TRANSCLI").smsService("TJC-SAT", "NOTIFICA TARJCRED")
                .smsService("CEX-SAT", "NOTIFICA COMEXT").smsService("TRC-VEN", "NOTIFICA TRANSCLI")
                .bceInstitution(GROUP + "BANCO DESTINO FAKE").currentOwner(Requests.ACCOUNT, 700001)
                .noHeaderRows();
    }

    private static Requests directOption04(String service) {
        return Requests.currentAccount().servicio(service).canal("DIR").opcion("04").aplcobis("N")
                .valorDebito("100.50").valorOrdenado("100.00");
    }

    @ParameterizedTest(name = "A17 + RULE-010: {0}, DIR, option 04, detail only in history -> @wRowdbBiz 0 -> 122004, full rollback")
    @ValueSource(strings = {"TRANSCLI", "TARJCRED", "COMEXT"})
    void a17_rule010_historyOnlyDetailRaises122004(String service) {
        FakeAseSession ase = interbank().interbankDetail(DetailTable.HISTORY, ORDER, GROUP, 4, "2200334455");

        DebitResult result = ase.service().debit(directOption04(service).build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).as("@wRowdbBiz from the LIVE read (840/912)").isEqualTo(0);
        assertThat(ase.headerUpdates()).as("option 04 on a direct channel runs no UPDATE").isEmpty();
        assertThat(ase.onlyEvent()).as("the event was sent before B12").isNotNull();
        assertThat(ase.committedWrites()).as("lbl_error: full rollback, the notification too").isEmpty();
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.commits()).isZero();
    }

    @Test
    @DisplayName("A17 + RULE-010 + RULE-028: same in COBIS mode -> sp_cerror(122004) and (122004, 0)")
    void a17_rule010_historyOnlyDetailCobis() {
        FakeAseSession ase = interbank().interbankDetail(DetailTable.HISTORY, ORDER, GROUP, 4, "2200334455");

        DebitResult result = ase.service().debit(directOption04("TRANSCLI").aplcobis("S").spName("sp_caller_fake").build());

        assertThat(result).isEqualTo(new DebitResult(122004, 0, null));
        assertThat(ase.onlyErrorReport()).isEqualTo(new ErrorReport("sp_caller_fake", 122004));
    }

    @Test
    @DisplayName("A17: detail found LIVE -> @wRowdbBiz 1 -> no 122004, commit (0, 0) with the ordered value")
    void a17_liveDetailCommits() {
        FakeAseSession ase = interbank().interbankDetail(DetailTable.LIVE, ORDER, GROUP, 4, "2200334455");

        DebitResult result = ase.service().debit(directOption04("TRANSCLI").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isEqualTo(1);
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("100.00");
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_eventos", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("A17: detail found nowhere -> @wRowdbBiz 0 -> 122004")
    void a17_detailNowhereRaises122004() {
        FakeAseSession ase = interbank();

        DebitResult result = ase.service().debit(directOption04("TRANSCLI").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("A17: two live rows -> @wRowdbBiz 2 -> commit")
    void a17_twoLiveRowsCommit() {
        FakeAseSession ase = interbank()
                .interbankDetail(DetailTable.LIVE, ORDER, GROUP, 3, "A-1")
                .interbankDetail(DetailTable.LIVE, ORDER, GROUP, 4, "B-2");

        DebitResult result = ase.service().debit(directOption04("TRANSCLI").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("A17: the VEN channel is direct too: TRANSCLI via 'TRC-VEN', detail only in history, option 04 -> 122004")
    void a17_venChannel() {
        FakeAseSession ase = interbank().interbankDetail(DetailTable.HISTORY, ORDER, GROUP, 4, "2200334455");

        DebitResult result = ase.service().debit(directOption04("TRANSCLI").canal("VEN").build());

        assertThat(result).isEqualTo(new DebitResult(0, 122004, null));
        assertThat(ase.onlyEvent().iDescCanal()).isEqualTo("Ventanilla");
    }

    @Test
    @DisplayName("A17 + RULE-010 + RULE-019: TRANSWIFT detail only in history -> @wRowdbBiz 0 but TRANSWIFT is exempt -> commit")
    void a17_rule019_transwiftHistoryOnlyIsExempt() {
        FakeAseSession ase = new FakeAseSession().smsService("TSW-SAT", "NOTIFICA TRANSWIFT").currentOwner(Requests.ACCOUNT, 1)
                .noHeaderRows().swiftDetail(DetailTable.HISTORY, ORDER, 0, Requests.COMPANY, "GRPHIS001", "CTA");

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("TRANSWIFT").codSwift("BICFAKEX")
                .canal("DIR").opcion("04").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isEqualTo(0);
        assertThat(ase.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("A17: TRANSWIFT detail live -> @wRowdbBiz 1")
    void a17_transwiftLive() {
        FakeAseSession ase = new FakeAseSession().smsService("TSW-SAT", "NOTIFICA TRANSWIFT").currentOwner(Requests.ACCOUNT, 1)
                .swiftDetail(DetailTable.LIVE, ORDER, 0, Requests.COMPANY, "GRPFAKE01", "CTA");

        ase.service().debit(Requests.currentAccount().servicio("TRANSWIFT").codSwift("BICFAKEX").opcion("04").build());

        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A17: TRANSCLI not configured (no SMS row) -> neither read ran -> @wRowdbBiz NULL -> commit (Phase 3 behavior)")
    void a17_notConfiguredKeepsNull() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows()
                .bceInstitution(GROUP + "BANCO DESTINO FAKE").interbankDetail(DetailTable.HISTORY, ORDER, GROUP, 4, "1");

        DebitResult result = ase.service().debit(directOption04("TRANSCLI").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isNull();
    }

    @Test
    @DisplayName("A17: ROLPAGO configured (no TRANSWIFT/interbank read) -> @wRowdbBiz NULL, even with a 'B' beneficiary read in history")
    void a17_otherServicesKeepNull() {
        FakeAseSession ase = new FakeAseSession().noHeaderRows().smsService("ROL-SAT", "NOTIFICA ROLPAGO")
                .notificationClassRow("ROLPAGO", "B").beneficiaryDetail(DetailTable.HISTORY, ORDER, "N", "G");

        DebitResult result = ase.service().debit(directOption04("ROLPAGO").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isNull();
    }

    @Test
    @DisplayName("A17 + RULE-014: with option 02 the B12 UPDATE runs and its rowcount replaces the B7 value (header live -> commit)")
    void a17_rule014_updateOverwritesTheB7Value() {
        FakeAseSession ase = new FakeAseSession()
                .smsService("TRC-SAT", "NOTIFICA TRANSCLI").bceInstitution(GROUP + "BANCO DESTINO FAKE")
                .currentOwner(Requests.ACCOUNT, 1).interbankDetail(DetailTable.HISTORY, ORDER, GROUP, 4, "1")
                .headerRow(ORDER, "CUE", "TRANSCLI", "I");

        DebitResult result = ase.service().debit(directOption04("TRANSCLI").opcion("02").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyOrderHeaderStep().priorRowCount()).isEqualTo(0);
        assertThat(ase.headerState(ORDER, "CUE", "TRANSCLI")).isEqualTo("T");
    }

    @Test
    @DisplayName("A17 + RULE-023 (line 1358): SPI return with the order live -> @wRowdbBiz 1; found only in history or nowhere -> 0")
    void a17_rule023_spiReturnSetsTheRowCount() {
        FakeAseSession live = new FakeAseSession().liveOrder(ORDER, "TRANSCLI");
        FakeAseSession history = new FakeAseSession().historyOrder(ORDER, "TRANSCLI");
        FakeAseSession nowhere = new FakeAseSession();

        live.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());
        history.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());
        nowhere.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").build());

        assertThat(live.onlyOrderHeaderStep().priorRowCount()).isEqualTo(1);
        assertThat(history.onlyOrderHeaderStep().priorRowCount()).isEqualTo(0);
        assertThat(nowhere.onlyOrderHeaderStep().priorRowCount()).isEqualTo(0);
        assertThat(live.onlyOrderHeaderStep().actTotord()).isEqualTo("N");
    }
}
