package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Blocks B2 and B3 (lines 254-394): accounting concept (RULE-029, confirmed as-is: @s_term is the
 * SPI concept carrier and is blanked), TRANSQUICK with an SPI payment form (RULE-030) and the
 * accounting configuration lookup whose failure is error 120000 raised BEFORE begin tran
 * (RULE-007, P0).
 */
class Rule007Rule029Rule030AccountingConfigurationTest {

    @Test
    @DisplayName("RULE-007: lookup receives producto, servicio, tipoafec, frm_pagcob, canal, tipcta, moneda char(2), tipo_referencia, empresa, concepto '0' (lines 350-374)")
    void rule007_accountingQueryArgumentsForCurrentAccount() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().producto(1).servicio("ROLPAGO").tipoAfec("10")
                .frmPagcob("CUE").canal("DIR").tipctaEmp(3).monDebito(1).tipoReferencia("N").empresa(500).build();

        ase.service().debit(request);

        assertThat(ase.onlyAccountingQuery())
                .isEqualTo(new AccountingConfigurationQuery(1, "ROLPAGO", "10", "CUE", "DIR", 3, "1 ", "N", 500, "0"));
    }

    @Test
    @DisplayName("RULE-007: moneda is convert(char(2)) -> two-digit currency 10 stays '10'")
    void rule007_monedaTwoDigits() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().monDebito(10).build());

        assertThat(ase.onlyAccountingQuery().moneda()).isEqualTo("10");
    }

    @Test
    @DisplayName("RULE-007: trn and causal returned by the lookup are the ones sent to the debit note and the movement")
    void rule007_trnAndCausalFlowToDebitNoteAndMovement() {
        FakeAseSession ase = new FakeAseSession().accounting(new AccountingConfiguration(0, 2745, "0331"));

        ase.service().debit(Requests.currentAccount().build());

        assertThat(ase.onlyDebitNote().tTrn()).isEqualTo(2745);
        assertThat(ase.onlyDebitNote().iCausal()).isEqualTo("0331");
        assertThat(ase.onlyMovement().tTrn()).isEqualTo(2745);
        assertThat(ase.onlyMovement().iCau()).isEqualTo("0331");
    }

    @Test
    @DisplayName("RULE-007 (P0): lookup return 1 -> 120000 via lbl_error, aplcobis 'N' -> return 0, o_error 120000, and NO begin tran (line 382-394)")
    void rule007_missingAccountingConfigurationAborts120000BeforeTransaction() {
        FakeAseSession ase = new FakeAseSession().accounting(new AccountingConfiguration(1, null, null));
        DebitRequest request = Requests.currentAccount().aplcobis("N").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 120000, null));
        assertThat(ase.begins()).isZero();
        assertThat(ase.rollbacks()).as("@@trancount = 0: no rollback tran").isZero();
        assertThat(ase.commits()).isZero();
        assertThat(ase.debitNotes()).isEmpty();
        assertThat(ase.movements()).isEmpty();
        assertThat(ase.errorReports()).as("sp_cerror only when aplcobis = 'S'").isEmpty();
        assertThat(ase.committedWrites()).isEmpty();
        assertThat(ase.calls()).containsExactly("sp_con_comision", "sp_con_confcontable");
    }

    @Test
    @DisplayName("RULE-007 + RULE-028: same failure with aplcobis 'S' -> sp_cerror(sp_name, 120000), return 120000, o_error stays 0")
    void rule007_rule028_missingAccountingConfigurationFromCobisReportsAndReturnsCode() {
        FakeAseSession ase = new FakeAseSession().accounting(new AccountingConfiguration(1, null, null));
        DebitRequest request = Requests.currentAccount().aplcobis("S").spName("sp_test_caller").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(120000, 0, null));
        assertThat(ase.onlyErrorReport()).isEqualTo(new ErrorReport("sp_test_caller", 120000));
        assertThat(ase.begins()).isZero();
        assertThat(ase.calls()).containsExactly("sp_con_comision", "sp_con_confcontable", "sp_cerror");
    }

    @Test
    @DisplayName("RULE-007: a SQL error (@@error <> 0) on the lookup is also 120000")
    void rule007_sqlErrorOnLookupIs120000() {
        FakeAseSession ase = new FakeAseSession().accountingLookupThrows();

        DebitResult result = ase.service().debit(Requests.currentAccount().build());

        assertThat(result).isEqualTo(new DebitResult(0, 120000, null));
        assertThat(ase.begins()).isZero();
    }

    @Test
    @DisplayName("RULE-007: negative return code from the lookup is NOT an error (only > 0 aborts)")
    void rule007_negativeReturnCodeIsNotAnError() {
        FakeAseSession ase = new FakeAseSession().accounting(new AccountingConfiguration(-1, 2701, "0150"));

        DebitResult result = ase.service().debit(Requests.currentAccount().build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.begins()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-029: SPI -> concepto = @s_term and @s_term blanked to ' ' for the debit note and the movement (lines 278-286)")
    void rule029_spiConceptFromTerminalAndTerminalBlanked() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().servicio("SPI").sTerm("07").build();

        ase.service().debit(request);

        assertThat(ase.onlyAccountingQuery().concepto()).isEqualTo("07");
        assertThat(ase.onlyDebitNote().sTerm()).isEqualTo(" ");
        assertThat(ase.onlyMovement().sTerm()).isEqualTo(" ");
        assertThat(ase.basicAccountChecks()).as("SPI never checks the basic account").isEmpty();
    }

    @Test
    @DisplayName("RULE-029: SPI with null @s_term -> concepto null, terminal still blanked")
    void rule029_spiNullTerminal() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("SPI").sTerm(null).build());

        assertThat(ase.onlyAccountingQuery().concepto()).isNull();
        assertThat(ase.onlyDebitNote().sTerm()).isEqualTo(" ");
    }

    @Test
    @DisplayName("RULE-029: type 12 basic account (vi_prod_banc 13) -> concepto '91' (lines 296-308)")
    void rule029_virtualBasicAccountConcept91() {
        FakeAseSession ase = new FakeAseSession().basicAccount(Requests.ACCOUNT);
        DebitRequest request = Requests.currentAccount().tipctaEmp(12).numctaEmp(Requests.ACCOUNT).build();

        ase.service().debit(request);

        assertThat(ase.basicAccountChecks()).containsExactly(Requests.ACCOUNT);
        assertThat(ase.onlyAccountingQuery().concepto()).isEqualTo("91");
    }

    @Test
    @DisplayName("RULE-029: type 12 non-basic account -> concepto '0'")
    void rule029_virtualNonBasicAccountConcept0() {
        FakeAseSession ase = new FakeAseSession(); // no basic accounts registered

        ase.service().debit(Requests.currentAccount().tipctaEmp(12).build());

        assertThat(ase.basicAccountChecks()).containsExactly(Requests.ACCOUNT);
        assertThat(ase.onlyAccountingQuery().concepto()).isEqualTo("0");
    }

    @Test
    @DisplayName("RULE-029: types 3 and 4 never consult vi_cuenta and use concepto '0'")
    void rule029_currentAndSavingsNeverCheckBasicAccount() {
        FakeAseSession ase3 = new FakeAseSession().basicAccount(Requests.ACCOUNT);
        FakeAseSession ase4 = new FakeAseSession().basicAccount(Requests.ACCOUNT);

        ase3.service().debit(Requests.currentAccount().tipctaEmp(3).build());
        ase4.service().debit(Requests.currentAccount().tipctaEmp(4).build());

        assertThat(ase3.basicAccountChecks()).isEmpty();
        assertThat(ase4.basicAccountChecks()).isEmpty();
        assertThat(ase3.onlyAccountingQuery().concepto()).isEqualTo("0");
        assertThat(ase4.onlyAccountingQuery().concepto()).isEqualTo("0");
        // @s_term is untouched for non-SPI services
        assertThat(ase3.onlyDebitNote().sTerm()).isEqualTo("TERM01");
    }

    @Test
    @DisplayName("RULE-030: TRANSQUICK with SPI form 'COB' -> lookup with frm_pagcob 'COB' and no concepto (lines 320-346)")
    void rule030_transquickWithSpiFormUsesSpiFormAndOmitsConcept() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().servicio("TRANSQUICK").frmPagcob("CUE").frmPagcobSpi("COB")
                .build();

        ase.service().debit(request);

        AccountingConfigurationQuery query = ase.onlyAccountingQuery();
        assertThat(query.frmPagcob()).isEqualTo("COB");
        assertThat(query.concepto()).as("@i_concepto is not passed on the SPI branch").isNull();
        // the main movement keeps the original payment form (RULE-025/030 edge)
        assertThat(ase.onlyMovement().iFrmPagcob()).isEqualTo("CUE");
        // sp_ndc_ahcc receives the SPI form as @i_frm_pagcob and the original as @i_alterno_dos (lines 716-720)
        assertThat(ase.onlyDebitNote().iFrmPagcob()).isEqualTo("COB");
        assertThat(ase.onlyDebitNote().iAlternoDos()).isEqualTo("CUE");
    }

    @Test
    @DisplayName("RULE-030: TRANSQUICK without SPI form follows the normal lookup with concepto '0'")
    void rule030_transquickWithoutSpiFormIsNormal() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("TRANSQUICK").frmPagcob("CUE").frmPagcobSpi(null).build());

        AccountingConfigurationQuery query = ase.onlyAccountingQuery();
        assertThat(query.frmPagcob()).isEqualTo("CUE");
        assertThat(query.concepto()).isEqualTo("0");
        assertThat(ase.onlyDebitNote().iFrmPagcob()).isNull();
    }

    @Test
    @DisplayName("RULE-030: a non-TRANSQUICK service ignores the SPI form for the lookup")
    void rule030_otherServicesIgnoreSpiFormForLookup() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").frmPagcob("CUE").frmPagcobSpi("COB").build());

        assertThat(ase.onlyAccountingQuery().frmPagcob()).isEqualTo("CUE");
        assertThat(ase.onlyAccountingQuery().concepto()).isEqualTo("0");
    }
}
