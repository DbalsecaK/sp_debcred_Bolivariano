package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B10, the first (separate) commission (lines 1496-1600): what {@code sp_grb_comision}
 * receives and when it is called at all. RULE-013 (P0) trigger and arguments, RULE-025 the
 * TRANSQUICK payment-form swap, RULE-001 the TRANSBIMO tariff, RULE-039 the REF33 pass-through.
 * Every expected value is read from the legacy lines cited; the working (mutated) variables
 * {@code @s_term}, {@code @s_ssn}, {@code @i_servicio}, {@code @w_cadena}, {@code @i_valor_comision}
 * are observed through the command the port receives.
 */
class Rule013Rule001Rule025Rule039CommissionTest {

    private static final String COBRO_DE_COMISION = "COBRO DE COMISION";

    @Test
    @DisplayName("RULE-013 (P0) + RULE-039: sp_grb_comision receives every legacy argument in legacy order (lines 1518-1600), reference literal, no tipoafec, savepoint, secuencial 0")
    void rule013_rule039_firstCommissionArgumentsInLegacyOrder() {
        FakeAseSession ase = new FakeAseSession().debitNote(0, 9001);
        DebitRequest request = Requests.currentAccount()
                .sSsn(77).sSrv("SRVTEST").sUser("usrtest").sTerm("TERM01").sOfi(1).aplcobis("N").spName("sp_test_caller")
                .canal("DIR").empresa(500).producto(1).servicio("ROLPAGO").tipoProceso("L").orden(123456)
                .tarjeta("4000XXXXXXXX0001").frmPagcob("CUE").monDebito(1).tipctaEmp(3).numctaEmp(Requests.ACCOUNT)
                .referencia("REF TEST 001").refProv("PROV-REF-01").tipoPagcob("P").paisCta(1).codBancoCta(34)
                .nemEmp("EMPTEST").localidadOrden(1).ordenEmpresa(4587).tipoReferencia("N")
                .comision("1.50").valorComision("2.00")
                .valorTarifa("1.00").valorComisionCue("0.10").valorTarifaEfe("1.50").valorComisionEfe("0.20")
                .valorTarifaChe("1.75").valorComisionChe("0.30")
                .build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.onlyCommissionCommand()).isEqualTo(new CommissionCommand(
                77, "SRVTEST", "usrtest", "TERM01", 1, "N", "sp_test_caller", Requests.PROCESS_DATE, "DIR", 500, 1,
                "ROLPAGO", "L", 123456, new BigDecimal("2.00"), "4587", "4000XXXXXXXX0001", "CUE", 1, 3, Requests.ACCOUNT,
                COBRO_DE_COMISION, "PROV-REF-01", "P", 1, 34, "EMPTEST", 1, null, null, 4587, "N",
                null /* no @i_tipoafec on the first call */, "sp_debito_empresa", 0,
                new BigDecimal("1.00"), new BigDecimal("0.10"), new BigDecimal("1.50"), new BigDecimal("0.20"),
                new BigDecimal("1.75"), new BigDecimal("0.30")));
        // the commission is charged inside the transaction, after the movement and before the header step
        assertThat(ase.calls()).endsWith("sp_grb_mov_y_frmpgo", "commissionStep", "sp_grb_comision", "orderHeaderStep", "commit");
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo", "sp_grb_comision");
        assertThat(ase.commits()).isEqualTo(1);
        assertThat(ase.rollbacks()).isZero();
        // the Phase 1 movement is untouched by the commission block: status 'P', the request reference
        assertThat(ase.onlyMovement().iEstProceso()).isEqualTo("P");
        assertThat(ase.onlyMovement().iReferencia()).isEqualTo("REF TEST 001");
    }

    @Test
    @DisplayName("RULE-013: @i_valor_comision = 0 -> sp_grb_comision is not called at all (line 1512)")
    void rule013_zeroSeparateCommissionSkipsTheBlock() {
        FakeAseSession ase = new FakeAseSession();

        DebitResult result = ase.service().debit(Requests.currentAccount().comision("0").valorComision("0").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.commissionCommands()).isEmpty();
        assertThat(ase.calls()).doesNotContain("sp_grb_comision");
        assertThat(ase.committedProcedures()).containsExactly("sp_ndc_ahcc", "sp_grb_mov_y_frmpgo");
    }

    @Test
    @DisplayName("RULE-013 + RULE-027: @i_valor_comision NULL (with @i_comision 2.50 bundled, no swap) -> no commission call, the block is skipped")
    void rule013_nullSeparateCommissionSkipsTheBlock() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().comision("2.50").valorComision(null).build());

        assertThat(ase.commissionCommands()).isEmpty();
        assertThat(ase.onlyCommissionStep().valorComision()).isNull();
        assertThat(ase.onlyDebitNote().iTcomision()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("RULE-013: a negative @i_valor_comision is not > 0 -> skipped")
    void rule013_negativeSeparateCommissionSkipsTheBlock() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().valorComision("-0.01").build());

        assertThat(ase.commissionCommands()).isEmpty();
    }

    @Test
    @DisplayName("RULE-013 + RULE-027: @i_comision 2.50 and @i_valor_comision 0 -> the moved value 2.50 IS charged by sp_grb_comision; the movement's valor_comision is 0")
    void rule013_rule027_normalizedBundledCommissionIsCharged() {
        FakeAseSession ase = new FakeAseSession().tariff(0, "0.50");

        DebitResult result = ase.service().debit(Requests.currentAccount().comision("2.50").valorComision("0").build());

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        CommissionCommand charged = ase.onlyCommissionCommand();
        assertThat(charged.iValorComision()).isEqualByComparingTo("2.50");
        assertThat(charged.iReferencia()).isEqualTo(COBRO_DE_COMISION);
        assertThat(ase.onlyDebitNote().iTcomision()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ase.onlyMovement().iValorComision()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RULE-025: TRANSQUICK with @i_frm_pagcob_spi 'COB' -> the commission uses 'COB' while the Phase 1 movement keeps 'CUE' (lines 1500-1502)")
    void rule025_transquickCommissionUsesSpiPaymentForm() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().servicio("TRANSQUICK").frmPagcob("CUE").frmPagcobSpi("COB")
                .valorComision("2.00").build();

        ase.service().debit(request);

        assertThat(ase.onlyCommissionCommand().iFrmPagcob()).isEqualTo("COB");
        assertThat(ase.onlyMovement().iFrmPagcob()).as("the main movement was written before the swap").isEqualTo("CUE");
        // the Phase 1 accounting lookup also used the SPI form (RULE-030), consistently
        assertThat(ase.onlyAccountingQuery().frmPagcob()).isEqualTo("COB");
    }

    @Test
    @DisplayName("RULE-025: the swap ignores trailing blanks in the service ('TRANSQUICK  ' = 'TRANSQUICK', Sybase =)")
    void rule025_transquickWithTrailingBlanksStillSwaps() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("TRANSQUICK  ").frmPagcob("CUE").frmPagcobSpi("COB")
                .valorComision("2.00").build());

        assertThat(ase.onlyCommissionCommand().iFrmPagcob()).isEqualTo("COB");
    }

    @Test
    @DisplayName("RULE-025: TRANSQUICK without an SPI form keeps 'CUE'; a non-TRANSQUICK service ignores the SPI form")
    void rule025_noSwapWithoutSpiFormOrForOtherServices() {
        FakeAseSession quickNoSpi = new FakeAseSession();
        FakeAseSession rolWithSpi = new FakeAseSession();

        quickNoSpi.service().debit(Requests.currentAccount().servicio("TRANSQUICK").frmPagcob("CUE").frmPagcobSpi(null)
                .valorComision("2.00").build());
        rolWithSpi.service().debit(Requests.currentAccount().servicio("ROLPAGO").frmPagcob("CUE").frmPagcobSpi("COB")
                .valorComision("2.00").build());

        assertThat(quickNoSpi.onlyCommissionCommand().iFrmPagcob()).isEqualTo("CUE");
        assertThat(rolWithSpi.onlyCommissionCommand().iFrmPagcob()).isEqualTo("CUE");
    }

    @Test
    @DisplayName("RULE-025 + RULE-013: the swapped form is the one on the exit-B 'X' movement too (line 1654)")
    void rule025_transquickSwapReachesTheExitBMovement() {
        FakeAseSession ase = new FakeAseSession().commission(0, 122010);

        DebitResult result = ase.service().debit(Requests.currentAccount().servicio("TRANSQUICK").frmPagcob("CUE")
                .frmPagcobSpi("COB").valorComision("2.00").build());

        assertThat(result).isEqualTo(new DebitResult(122010, 122010, null));
        assertThat(ase.movements()).hasSize(2);
        assertThat(ase.movements().get(0).iFrmPagcob()).isEqualTo("CUE");
        assertThat(ase.lastMovement().iFrmPagcob()).isEqualTo("COB");
    }

    @Test
    @DisplayName("RULE-001 (confirmed as-is): TRANSBIMO -> @i_valor_tarifa = @i_valor_comision (2.50 replaces the caller's 1.00); the cue/efe/che pairs are untouched (1506-1508)")
    void rule001_transbimoTariffEqualsSeparateCommission() {
        FakeAseSession ase = new FakeAseSession();
        DebitRequest request = Requests.currentAccount().servicio("TRANSBIMO").valorComision("2.50").valorTarifa("1.00")
                .valorComisionCue("0.10").valorTarifaEfe("1.50").valorComisionEfe("0.20")
                .valorTarifaChe("1.75").valorComisionChe("0.30").build();

        ase.service().debit(request);

        CommissionCommand charged = ase.onlyCommissionCommand();
        assertThat(charged.iValorTarifa()).isEqualByComparingTo("2.50");
        assertThat(charged.iValorComision()).isEqualByComparingTo("2.50");
        assertThat(charged.iValorComisionCue()).isEqualByComparingTo("0.10");
        assertThat(charged.iValorTarifaEfe()).isEqualByComparingTo("1.50");
        assertThat(charged.iValorComisionEfe()).isEqualByComparingTo("0.20");
        assertThat(charged.iValorTarifaChe()).isEqualByComparingTo("1.75");
        assertThat(charged.iValorComisionChe()).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("RULE-001 + RULE-027: TRANSBIMO with the bundled 3.00 moved to the separate commission -> tariff 3.00 (the normalized value, not the request's)")
    void rule001_transbimoTariffUsesTheNormalizedCommission() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("TRANSBIMO").comision("3.00").valorComision("0")
                .valorTarifa("1.00").build());

        assertThat(ase.onlyCommissionCommand().iValorTarifa()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("RULE-001: a non-TRANSBIMO service passes the caller's tariff as-is, null included")
    void rule001_otherServicesPassTariffAsIs() {
        FakeAseSession withTariff = new FakeAseSession();
        FakeAseSession nullTariff = new FakeAseSession();

        withTariff.service().debit(Requests.currentAccount().servicio("ROLPAGO").valorComision("2.50").valorTarifa("1.00").build());
        nullTariff.service().debit(Requests.currentAccount().servicio("ROLPAGO").valorComision("2.50").valorTarifa(null).build());

        assertThat(withTariff.onlyCommissionCommand().iValorTarifa()).isEqualByComparingTo("1.00");
        assertThat(nullTariff.onlyCommissionCommand().iValorTarifa()).isNull();
    }

    @Test
    @DisplayName("RULE-039: null REF33 values reach sp_grb_comision as null (defaults are null, nothing is computed)")
    void rule039_nullBreakdownIsPassedAsNull() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().valorComision("2.00").build());

        CommissionCommand charged = ase.onlyCommissionCommand();
        assertThat(charged.iValorTarifa()).isNull();
        assertThat(charged.iValorComisionCue()).isNull();
        assertThat(charged.iValorTarifaEfe()).isNull();
        assertThat(charged.iValorComisionEfe()).isNull();
        assertThat(charged.iValorTarifaChe()).isNull();
        assertThat(charged.iValorComisionChe()).isNull();
    }

    @Test
    @DisplayName("RULE-013 + RULE-029: for SPI the commission goes out with the blanked terminal ' ' and @s_ssn NULL -> 0 (working variables of lines 266, 286)")
    void rule013_spiCommissionUsesTheWorkingTerminalAndSsn() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("SPI").sTerm("TERM01").sSsn(null).valorComision("2.00").build());

        CommissionCommand charged = ase.onlyCommissionCommand();
        assertThat(charged.sTerm()).isEqualTo(" ");
        assertThat(charged.sSsn()).isEqualTo(0);
        assertThat(charged.iServicio()).isEqualTo("SPI");
    }

    @Test
    @DisplayName("RULE-013 + RULE-023: an SPI return charges the commission under the looked-up order service (mutated @i_servicio, line 1352 -> 1542)")
    void rule013_spiReturnCommissionUsesTheLookedUpService() {
        FakeAseSession ase = new FakeAseSession().liveOrder(Requests.BANK_ORDER, "TRANSCLI");

        ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").valorComision("2.00").build());

        assertThat(ase.onlyCommissionCommand().iServicio()).isEqualTo("TRANSCLI");
        assertThat(ase.onlyCommissionCommand().iCadena()).as("SPI return reference (line 614)").isEqualTo("4587");
    }

    @Test
    @DisplayName("RULE-013 + RULE-038: @i_cadena is the Phase 1 reference string: 'COD:'+swift for TRANSWIFT, company order for others, NULL for type 9 (never assigned)")
    void rule013_cadenaIsThePhase1ReferenceString() {
        FakeAseSession swift = new FakeAseSession();
        FakeAseSession plain = new FakeAseSession();
        FakeAseSession ledger = new FakeAseSession();

        swift.service().debit(Requests.currentAccount().servicio("TRANSWIFT").codSwift("ABCDEC2X").valorComision("2.00").build());
        plain.service().debit(Requests.currentAccount().servicio("ROLPAGO").ordenEmpresa(4587).valorComision("2.00").build());
        ledger.service().debit(Requests.currentAccount().tipctaEmp(9).valorComision("2.00").build());

        assertThat(swift.onlyCommissionCommand().iCadena()).isEqualTo("COD:ABCDEC2X");
        assertThat(swift.onlyCommissionCommand().iTipoafec()).as("valor2_swift is 0: only the first call").isNull();
        assertThat(plain.onlyCommissionCommand().iCadena()).isEqualTo("4587");
        assertThat(ledger.onlyCommissionCommand().iCadena()).isNull();
        assertThat(ledger.committedProcedures()).containsExactly("sp_graba_tran_servicio", "sp_grb_mov_y_frmpgo", "sp_grb_comision");
    }

    @Test
    @DisplayName("RULE-013 + RULE-031: after the CORPEI re-resolution the commission carries the re-resolved trn/causal through the exit-B movement (@w_trn, @w_causal)")
    void rule013_exitBMovementUsesTheReResolvedTransactionCode() {
        FakeAseSession ase = new FakeAseSession()
                .accounting(new AccountingConfiguration(0, 2701, "0150"), new AccountingConfiguration(0, 2999, null))
                .commission(0, 122010);

        ase.service().debit(Requests.currentAccount().servicio("IMPADUAN").codSwift("CORPEI").valorComision("2.00").build());

        assertThat(ase.lastMovement().tTrn()).isEqualTo(2999);
        assertThat(ase.lastMovement().iCau()).as("causal NULL -> '512' (line 548)").isEqualTo("512");
    }

    @Test
    @DisplayName("RULE-013: the commission step is not reached on exit A (debit failed) even with @i_valor_comision > 0")
    void rule013_notReachedOnExitA() {
        FakeAseSession ase = new FakeAseSession().debitNote(201045, null);

        DebitResult result = ase.service().debit(Requests.currentAccount().valorComision("2.00").build());

        assertThat(result).isEqualTo(new DebitResult(201045, 201045, null));
        assertThat(ase.commissionCommands()).isEmpty();
        assertThat(ase.commissionSteps()).isEmpty();
    }
}
