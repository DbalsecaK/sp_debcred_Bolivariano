package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.FakeAseSession.HeaderRow;
import com.nexti.debcred.support.FakeAseSession.HeaderTable;
import com.nexti.debcred.support.FakeAseSession.HeaderUpdate;
import com.nexti.debcred.support.Requests;

/**
 * Brief section 4, walkthrough 1 (the Phase 3 exit criterion), with the notification step stubbed
 * (Phase 4): the corporate treasurer's TRANSCLI payment order debits the company's current account
 * (sp_ndc_ahcc), records the movement (sp_grb_mov_y_frmpgo), charges the separate commission
 * (sp_grb_comision) and moves the order header from 'I' to 'T' (B12), then commits.
 * Rules: RULE-009, RULE-012, RULE-013, RULE-014, RULE-018. Fake data only.
 */
class Walkthrough1EndToEndTest {

    private static final int ORDER = Requests.BANK_ORDER;

    @Test
    @DisplayName("Walkthrough 1 (RULE-009, RULE-012, RULE-013, RULE-014): TRANSCLI, current account, commission, header I -> T, commit")
    void walkthrough1_rule014_rule013_rule009_transcliDebitCommissionAndHeaderTransition() {
        FakeAseSession ase = new FakeAseSession()
                .headerRow(ORDER, "CUE", "TRANSCLI", "I")
                .headerRow(ORDER, "EFE", "TRANSCLI", null)
                .headerRow(ORDER, "CHE", "TRANSCLI", "I")
                .headerRow(999001, "CUE", "TRANSCLI", "I")
                .historyHeaderRow(ORDER, "CUE", "TRANSCLI", "I");
        DebitRequest request = Requests.currentAccount().servicio("TRANSCLI").tipctaEmp(3).canal("DIR").opcion("02")
                .frmPagcob("CUE").comision("0").valorComision("1.50").valorDebito("250.00").valorOrdenado("250.00")
                .aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision",
                "sp_con_confcontable",
                "begin",
                "savepoint:sp_debito_empresa",
                "sp_ndc_ahcc",
                "notificationStep",
                "sp_grb_mov_y_frmpgo",
                "commissionStep",
                "sp_grb_comision",
                "orderHeaderStep",
                "commit");
        assertThat(ase.headerUpdates()).containsExactly(
                new HeaderUpdate(HeaderTable.LIVE, ORDER, List.of("CUE", "EFE", "CHL"), "TRANSCLI", 0, 2));

        // committed state
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo", "sp_grb_comision");
        assertThat(ase.onlyDebitNote().iValor()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyMovement().iServicio()).isEqualTo("TRANSCLI");
        assertThat(ase.onlyCommissionCommand().iValorComision()).isEqualByComparingTo(new BigDecimal("1.50"));
        assertThat(ase.headerRows(HeaderTable.LIVE)).containsExactly(
                new HeaderRow(ORDER, "CUE", "TRANSCLI", "T", 0),
                new HeaderRow(ORDER, "EFE", "TRANSCLI", "T", 0),
                new HeaderRow(ORDER, "CHE", "TRANSCLI", "I", null),
                new HeaderRow(999001, "CUE", "TRANSCLI", "I", null));
        assertThat(ase.headerRows(HeaderTable.HISTORY)).as("live hit: history untouched (RULE-018)")
                .containsExactly(new HeaderRow(ORDER, "CUE", "TRANSCLI", "I", null));
        assertThat(ase.begins()).isEqualTo(1);
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.rollbacks()).isZero();
        assertThat(ase.errorReports()).isEmpty();
        assertThat(ase.transactionOpen()).isFalse();
    }
}
