package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B8 (lines 1264-1300): accounting account (type 9) debited through
 * db_biz_pagos..sp_graba_tran_servicio, with the company-1295 / SPI special case (RULE-022,
 * confirmed as-is: the hardcoded company and the zeroed movement value are behavior to preserve).
 */
class Rule022LedgerDebitTest {

    @Test
    @DisplayName("RULE-022 + RULE-009: type 9 -> sp_graba_tran_servicio with indicador 1, oficina_cta 0, tipo_chequera '500', saldo NULL, valor 250.00 (lines 1284-1298)")
    void rule022_ledgerDebitArgumentsForOrdinaryCompany() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().tipctaEmp(9).empresa(500).servicio("ROLPAGO")
                .sSrv("SRVTEST").sOfi(5).sSsn(77).sUser("usrtest").sTerm("TERM01").valorDebito("250.00").monDebito(1)
                .refProv("PROV-REF-01").orden(123456).build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyLedgerDebit()).isEqualTo(new LedgerDebitCommand(
                "SRVTEST", 5, null, "usrtest", "TERM01", 2701, Requests.PROCESS_DATE, "PROV-REF-01", Requests.ACCOUNT,
                5, 1, 1, "0150", null, new BigDecimal("250.00"), 0, "500", 123456, 0));
        assertThat(ase.debitNotes()).isEmpty();
        assertThat(ase.virtualDebitNotes()).isEmpty();
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("250.00");
        assertThat(ase.committedProcedures()).containsExactly("sp_graba_tran_servicio", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-022: type 9 has no notification step and no basic-account check")
    void rule022_ledgerPathSkipsNotificationAndBasicAccountCheck() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().tipctaEmp(9).build());

        assertThat(ase.notifications()).isEmpty();
        assertThat(ase.basicAccountChecks()).isEmpty();
        assertThat(ase.calls()).containsExactly(
                "sp_con_comision", "sp_con_confcontable", "begin", "savepoint:sp_debito_empresa",
                "sp_graba_tran_servicio", "sp_grb_mov_y_frmpgo", "commissionStep", "orderHeaderStep", "commit");
    }

    @Test
    @DisplayName("RULE-022: company 1295 + SPI -> saldo 250.00, valor 0, and the movement value is also 0 (lines 1276-1278, 1424)")
    void rule022_company1295SpiBooksBalanceInsteadOfValueAndZeroesMovement() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().tipctaEmp(9).empresa(1295).servicio("SPI").valorDebito("250.00")
                .build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        LedgerDebitCommand ledger = ase.onlyLedgerDebit();
        assertThat(ledger.iSaldo()).isEqualByComparingTo("250.00");
        assertThat(ledger.iValor()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ledger.iTipoChequera()).isEqualTo("1295");
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo(BigDecimal.ZERO);
        // the movement still reports the ordered value as received
        assertThat(ase.onlyMovement().iValorOrdenado()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("RULE-022: company 1295 on a non-SPI service -> ordinary booking (saldo NULL, valor 250.00)")
    void rule022_company1295NonSpiIsOrdinary() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().tipctaEmp(9).empresa(1295).servicio("ROLPAGO").valorDebito("250.00").build());

        assertThat(ase.onlyLedgerDebit().iSaldo()).isNull();
        assertThat(ase.onlyLedgerDebit().iValor()).isEqualByComparingTo("250.00");
        assertThat(ase.onlyMovement().iValorMov()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("RULE-022: SPI for another company (500) -> ordinary booking")
    void rule022_otherCompanySpiIsOrdinary() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().tipctaEmp(9).empresa(500).servicio("SPI").valorDebito("250.00").build());

        assertThat(ase.onlyLedgerDebit().iSaldo()).isNull();
        assertThat(ase.onlyLedgerDebit().iValor()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("RULE-022: 1295 + SPI with a padded service 'SPI ' matches (trailing blanks ignored) but ' SPI' does not")
    void rule022_spiComparisonIgnoresTrailingBlanksOnly() {
        FakeAseSession trailing = new FakeAseSession();
        FakeAseSession leading = new FakeAseSession();

        trailing.service().debit(Requests.currentAccount().tipctaEmp(9).empresa(1295).servicio("SPI ").valorDebito("250.00").build());
        leading.service().debit(Requests.currentAccount().tipctaEmp(9).empresa(1295).servicio(" SPI").valorDebito("250.00").build());

        assertThat(trailing.onlyLedgerDebit().iValor()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(leading.onlyLedgerDebit().iValor()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("RULE-022 + RULE-012: ledger debit return 300100 -> exit A: savepoint rollback, movement 'X' with 300100 committed, return 300100")
    void rule022_ledgerDebitFailureIsExitA() {
        FakeAseSession ase = new FakeAseSession().ledgerDebit(300100);

        DebitResult result = ase.service().debit(Requests.currentAccount().tipctaEmp(9).build());

        assertThat(result).isEqualTo(new DebitResult(300100, 300100, null));
        assertThat(ase.calls()).containsSubsequence("sp_graba_tran_servicio", "rollbackToSavepoint:sp_debito_empresa",
                "sp_grb_mov_y_frmpgo", "commit");
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("X");
        assertThat(ase.onlyMovement().iCodError()).isEqualTo(300100);
        assertThat(ase.committedProcedures()).containsExactly("sp_grb_mov_y_frmpgo");
    }
}
