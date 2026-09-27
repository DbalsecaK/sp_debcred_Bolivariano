package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

/**
 * Block B5 (lines 422-614): the debit-note reference string (RULE-038), the IMPADUAN/CORPEI causal
 * re-resolution with fallback '512' (RULE-031) and the SPI-return reference (RULE-032). All of it
 * happens after begin tran, inside the account-type 3/4/12 branch.
 */
class Rule038Rule031Rule032DebitReferenceTest {

    @Test
    @DisplayName("RULE-038: default reference is the company order number as text ('4587')")
    void rule038_defaultReferenceIsCompanyOrderNumber() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("ROLPAGO").ordenEmpresa(4587).build());

        assertThat(ase.onlyDebitNote().iRef()).isEqualTo("4587");
    }

    @Test
    @DisplayName("RULE-038: TRANSWIFT -> 'COD:' + swift code (line 438)")
    void rule038_transwiftReferenceIsCodPlusSwift() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("TRANSWIFT").codSwift("ABC123").build());

        assertThat(ase.onlyDebitNote().iRef()).isEqualTo("COD:ABC123");
    }

    @Test
    @DisplayName("RULE-038: TRANSWIFT with padded service name ' TRANSWIFT ' still matches (ltrim/rtrim)")
    void rule038_transwiftMatchIsTrimmed() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio(" TRANSWIFT ").codSwift("XYZ").build());

        assertThat(ase.onlyDebitNote().iRef()).isEqualTo("COD:XYZ");
    }

    @Test
    @DisplayName("RULE-038: TRANSWIFT with null swift code -> null reference (NULL concatenation)")
    void rule038_transwiftNullSwiftGivesNullReference() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("TRANSWIFT").codSwift(null).build());

        assertThat(ase.onlyDebitNote().iRef()).isNull();
    }

    @Test
    @DisplayName("RULE-038: the reference also reaches the virtual debit note (type 12) as @i_ref")
    void rule038_referenceReachesVirtualDebitNote() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().tipctaEmp(12).ordenEmpresa(4587).build());

        assertThat(ase.onlyVirtualDebitNote().iRef()).isEqualTo("4587");
    }

    @Test
    @DisplayName("RULE-031: IMPADUAN + CORPEI -> second lookup with catalog concept '77' and frm_pagcob, causal from it (lines 456-528)")
    void rule031_impaduanCorpeiUsesNdcorpeiConcept() {
        FakeAseSession ase = new FakeAseSession()
                .ndcorpeiConcept("77")
                .accounting(new AccountingConfiguration(0, 2701, "0150"), new AccountingConfiguration(0, 2799, "0777"));
        DebitRequest request = Requests.currentAccount().servicio("IMPADUAN").codSwift("CORPEI").frmPagcob("CUE").build();

        DebitResult result = ase.service().debit(request);

        assertThat(result).isEqualTo(new DebitResult(0, 0, null));
        assertThat(ase.accountingQueries()).hasSize(2);
        AccountingConfigurationQuery second = ase.accountingQueries().get(1);
        assertThat(second.concepto()).isEqualTo("77");
        assertThat(second.frmPagcob()).isEqualTo("CUE");
        assertThat(second.servicio()).isEqualTo("IMPADUAN");
        assertThat(ase.catalogReads()).isEqualTo(1);
        // the second lookup happens after begin tran + savepoint
        assertThat(ase.calls()).containsSubsequence("begin", "savepoint:sp_debito_empresa", "ba_catalogo:NDCORPEI",
                "sp_con_confcontable", "sp_ndc_ahcc");
        assertThat(ase.onlyDebitNote().iCausal()).isEqualTo("0777");
        assertThat(ase.onlyDebitNote().tTrn()).isEqualTo(2799);
        assertThat(ase.onlyMovement().iCau()).isEqualTo("0777");
    }

    @Test
    @DisplayName("RULE-031: CORPEI lookup returns null causal -> fallback '512' (line 528)")
    void rule031_corpeiNullCausalFallsBackTo512() {
        FakeAseSession ase = new FakeAseSession()
                .ndcorpeiConcept("77")
                .accounting(new AccountingConfiguration(0, 2701, "0150"), new AccountingConfiguration(0, 2799, null));

        ase.service().debit(Requests.currentAccount().servicio("IMPADUAN").codSwift("CORPEI").build());

        assertThat(ase.onlyDebitNote().iCausal()).isEqualTo("512");
        assertThat(ase.onlyMovement().iCau()).isEqualTo("512");
    }

    @Test
    @DisplayName("RULE-031: catalog row missing -> concept '99999' is sent (line 480)")
    void rule031_missingCatalogRowSendsConcept99999() {
        FakeAseSession ase = new FakeAseSession()
                .accounting(new AccountingConfiguration(0, 2701, "0150"), new AccountingConfiguration(0, 2799, "0512"));

        ase.service().debit(Requests.currentAccount().servicio("IMPADUAN").codSwift("CORPEI").build());

        assertThat(ase.accountingQueries().get(1).concepto()).isEqualTo("99999");
    }

    @Test
    @DisplayName("RULE-031: CORPEI lookup return 1 -> 120000 via lbl_error AFTER begin tran: rollback, nothing committed (line 532-544)")
    void rule031_corpeiLookupFailureRollsBackWith120000() {
        FakeAseSession ase = new FakeAseSession()
                .ndcorpeiConcept("77")
                .accounting(new AccountingConfiguration(0, 2701, "0150"), new AccountingConfiguration(1, null, null));

        DebitResult result = ase.service().debit(
                Requests.currentAccount().servicio("IMPADUAN").codSwift("CORPEI").aplcobis("N").build());

        assertThat(result).isEqualTo(new DebitResult(0, 120000, null));
        assertThat(ase.begins()).isEqualTo(1);
        assertThat(ase.rollbacks()).isEqualTo(1);
        assertThat(ase.commits()).isZero();
        assertThat(ase.debitNotes()).isEmpty();
        assertThat(ase.movements()).isEmpty();
        assertThat(ase.committedWrites()).isEmpty();
        assertThat(ase.calls()).containsSubsequence("begin", "sp_con_confcontable", "rollback");
    }

    @Test
    @DisplayName("RULE-031: IMPADUAN with swift 'OTHER' or null -> no re-resolution, causal from the first lookup")
    void rule031_impaduanNonCorpeiNoReResolution() {
        FakeAseSession aseOther = new FakeAseSession().ndcorpeiConcept("77");
        FakeAseSession aseNull = new FakeAseSession().ndcorpeiConcept("77");

        aseOther.service().debit(Requests.currentAccount().servicio("IMPADUAN").codSwift("OTHER").build());
        aseNull.service().debit(Requests.currentAccount().servicio("IMPADUAN").codSwift(null).build());

        assertThat(aseOther.accountingQueries()).hasSize(1);
        assertThat(aseNull.accountingQueries()).hasSize(1);
        assertThat(aseOther.catalogReads()).isZero();
        assertThat(aseOther.onlyDebitNote().iCausal()).isEqualTo("0150");
    }

    @Test
    @DisplayName("RULE-031: swift ' CORPEI ' matches (ltrim/rtrim) and 'corpei' does not")
    void rule031_corpeiMatchTrimmedCaseSensitive() {
        FakeAseSession padded = new FakeAseSession().ndcorpeiConcept("77")
                .accounting(new AccountingConfiguration(0, 2701, "0150"), new AccountingConfiguration(0, 2799, "0777"));
        FakeAseSession lower = new FakeAseSession().ndcorpeiConcept("77");

        padded.service().debit(Requests.currentAccount().servicio("IMPADUAN").codSwift(" CORPEI ").build());
        lower.service().debit(Requests.currentAccount().servicio("IMPADUAN").codSwift("corpei").build());

        assertThat(padded.accountingQueries()).hasSize(2);
        assertThat(lower.accountingQueries()).hasSize(1);
    }

    @Test
    @DisplayName("RULE-031: IMPADUAN + CORPEI on a ledger account (type 9) is outside the 3/4/12 branch: no re-resolution")
    void rule031_ledgerAccountSkipsCorpeiReResolution() {
        FakeAseSession ase = new FakeAseSession().ndcorpeiConcept("77");

        ase.service().debit(Requests.currentAccount().tipctaEmp(9).servicio("IMPADUAN").codSwift("CORPEI").build());

        assertThat(ase.accountingQueries()).hasSize(1);
        assertThat(ase.catalogReads()).isZero();
        assertThat(ase.onlyLedgerDebit().iCausa()).isEqualTo("0150");
    }

    @Test
    @DisplayName("RULE-032: SPI with affectation '12' -> reference is the company order number (line 612)")
    void rule032_spiReturnReferenceIsCompanyOrderNumber() {
        FakeAseSession ase = new FakeAseSession().liveOrder(Requests.BANK_ORDER, "TRANSCLI");

        ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("12").ordenEmpresa(4587).build());

        assertThat(ase.onlyDebitNote().iRef()).isEqualTo("4587");
    }

    @Test
    @DisplayName("RULE-032: SPI with affectation '10' keeps the default reference too (no-op duplicate preserved)")
    void rule032_spiNormalAffectationSameReference() {
        FakeAseSession ase = new FakeAseSession();

        ase.service().debit(Requests.currentAccount().servicio("SPI").tipoAfec("10").ordenEmpresa(4587).build());

        assertThat(ase.onlyDebitNote().iRef()).isEqualTo("4587");
    }
}
