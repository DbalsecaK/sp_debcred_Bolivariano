package com.nexti.debcred;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The company-account debit for a payment order, formerly {@code dbo.sp_debcred_empresa}
 * (sp_debcred_empresa.sp). Phase 1: blocks B0-B6, B8, B9, B14 and B15; notifications (B7),
 * commissions (B10-B11) and the order-header update (B12) are steps that later phases fill in.
 *
 * <p>Business logic is preserved as-is (INTENT.md, 2026-09-27), including the behaviors the
 * assessment flagged and the approver confirmed: a notification failure reverses the debit
 * (RULE-012, line 1248), company 1295 on SPI (RULE-022), the terminal carrying the SPI concept
 * (RULE-029), the truncated commission count (RULE-002).
 *
 * <p>Three exits, over one ASE transaction ({@link AseTransaction}):
 * <ul>
 *   <li><b>Exit A</b> (lines 1334-1492): the debit failed; rollback to the savepoint, record the
 *       movement with status {@code X}, <b>commit</b>, return the code (RULE-012, RULE-015).</li>
 *   <li><b>Exit C</b> ({@code lbl_error}, 2140-2170): full rollback if a transaction is open;
 *       COBIS mode returns the code and reports it; otherwise return 0 with {@code @o_error} set
 *       (RULE-028).</li>
 *   <li><b>Success</b> (2130-2136): commit, return 0.</li>
 * </ul>
 * <b>Exit B</b> (a failed commission, lines 1606-1700) is decided by {@link CommissionDebits}: full rollback,
 * failure movement in autocommit, return without commit.
 */
public class DebitCompanyAccountService {

    /** {@code @w_savepoint} (line 406); the legacy name does not match the procedure's, and stays. */
    public static final String SAVEPOINT = "sp_debito_empresa";

    /** No accounting configuration (lines 390, 538). */
    public static final int NO_ACCOUNTING_CONFIGURATION = 120000;
    /** Unsupported company account type (line 1316). RULE-008. */
    public static final int UNSUPPORTED_ACCOUNT_TYPE = 122001;
    /** The movement could not be recorded (line 1472). RULE-015. */
    public static final int MOVEMENT_NOT_RECORDED = 122002;

    private static final int CURRENT_ACCOUNT = 3;
    private static final int SAVINGS_ACCOUNT = 4;
    private static final int ACCOUNTING_ACCOUNT = 9;
    private static final int VIRTUAL_ACCOUNT = 12;
    private static final String COMPANY_WITH_BALANCE_BOOKING = "1295";                        // REF45

    private final AseTransaction tx;
    private final CommissionTariffPort commissionTariff;
    private final AccountingConfigurationPort accountingConfiguration;
    private final DebitNotePort debitNote;
    private final VirtualDebitNotePort virtualDebitNote;
    private final LedgerDebitPort ledgerDebit;
    private final MovementPort movement;
    private final ErrorReportingPort errorReporting;
    private final NotificationStep notification;
    private final CommissionStep commission;
    private final OrderHeaderStep orderHeader;
    private final CatalogReader catalog;
    private final VirtualAccountReader virtualAccounts;
    private final OrderReader orders;

    public DebitCompanyAccountService(AseTransaction tx,
                                      CommissionTariffPort commissionTariff,
                                      AccountingConfigurationPort accountingConfiguration,
                                      DebitNotePort debitNote,
                                      VirtualDebitNotePort virtualDebitNote,
                                      LedgerDebitPort ledgerDebit,
                                      MovementPort movement,
                                      ErrorReportingPort errorReporting,
                                      NotificationStep notification,
                                      CommissionStep commission,
                                      OrderHeaderStep orderHeader,
                                      CatalogReader catalog,
                                      VirtualAccountReader virtualAccounts,
                                      OrderReader orders) {
        this.tx = tx;
        this.commissionTariff = commissionTariff;
        this.accountingConfiguration = accountingConfiguration;
        this.debitNote = debitNote;
        this.virtualDebitNote = virtualDebitNote;
        this.ledgerDebit = ledgerDebit;
        this.movement = movement;
        this.errorReporting = errorReporting;
        this.notification = notification;
        this.commission = commission;
        this.orderHeader = orderHeader;
        this.catalog = catalog;
        this.virtualAccounts = virtualAccounts;
        this.orders = orders;
    }

    /** The idiomatic wiring: one ASE session carries the transaction and every port (architecture review M5). */
    public DebitCompanyAccountService(AseSession ase, NotificationStep notification, CommissionStep commission,
                                      OrderHeaderStep orderHeader) {
        this(ase, ase, ase, ase, ase, ase, ase, ase, notification, commission, orderHeader, ase, ase, ase);
    }

    /** One execution of the procedure. Never throws for a business outcome. */
    public DebitResult debit(DebitRequest request) {
        // B1 (168-272)
        Boolean batch = batchFlag(request.iAplcobis());                                          // RULE-026, RULE-037
        CommissionBreakdown commissions = CommissionBreakdown.of(request, commissionTariff);      // RULE-027, RULE-002
        String moneda = AseText.toChar(request.iMonDebito(), 2);                                 // 254
        String frmPagcobDeb = request.iFrmPagcobDeb() != null ? request.iFrmPagcobDeb() : request.iFrmPagcob(); // 262
        Integer sSsn = request.sSsn() == null ? 0 : request.sSsn();                              // 266

        // B2 (276-314): the concept; for SPI the terminal carries it and is blanked afterwards
        String terminal = request.sTerm();
        String conceptoBas;
        if (AseText.equalsIgnoringTrailingBlanks(request.iServicio(), "SPI")) {                   // RULE-029, ref41
            conceptoBas = terminal;
            terminal = " ";
        } else {
            conceptoBas = "0";
            if (Objects.equals(request.iTipctaEmp(), VIRTUAL_ACCOUNT)
                    && virtualAccounts.isBasicAccount(request.iNumctaEmp())) {
                conceptoBas = "91";
            }
        }

        // B3 (318-396): transaction code and causal, before any transaction
        AccountingConfiguration accounting;
        try {
            accounting = accountingConfiguration.resolve(accountingQuery(request, moneda, conceptoBas));   // RULE-007, RULE-030
        } catch (AsePortException failure) {
            return errorExit(request, NO_ACCOUNTING_CONFIGURATION, false);                     // @@error <> 0 (382)
        }
        if (accounting.returnCode() > 0) {
            return errorExit(request, NO_ACCOUNTING_CONFIGURATION, false);
        }
        Integer trn = accounting.trn();
        String causal = accounting.causal();

        // B4 (402-408)
        tx.begin();
        tx.savepoint(SAVEPOINT);

        // B5, B6, B8 (414-740, 1260-1322)
        Posting posting = post(request, sSsn, terminal, moneda, trn, causal, batch, commissions);
        if (posting instanceof Posting.Aborted aborted) {
            return errorExit(request, aborted.numError(), true);
        }
        Posting.Posted posted = (Posting.Posted) posting;

        // B9 (1332-1492)
        String stsProc = "P";
        if (posted.codErrord() != 0) {                                                            // RULE-012
            tx.rollbackToSavepoint(SAVEPOINT);
            stsProc = "X";
        }
        String servicio = request.iServicio();
        String actTotord = "S";
        if (isSpiReturn(request)) {                                                               // RULE-023, RULE-024
            servicio = orders.liveService(request.iOrden())
                    .or(() -> orders.historyService(request.iOrden()))
                    .orElse(null);
            actTotord = "N";
        }
        MovementResult recorded;
        try {
            recorded = movement.record(movementCommand(request, terminal, posted, stsProc, servicio, commissions));  // RULE-015
        } catch (AsePortException failure) {
            return errorExit(request, MOVEMENT_NOT_RECORDED, true);                            // @@error <> 0 (1464)
        }
        if (recorded.returnCode() != 0) {
            return errorExit(request, MOVEMENT_NOT_RECORDED, true);
        }
        if (posted.codErrord() != 0) {                                                            // exit A (1480-1492)
            tx.commit();
            return new DebitResult(posted.codErrord(), posted.codErrord(), null);
        }

        // B10-B11 (1496-1856): the commissions decide how the debit ends
        CommissionOutcome outcome = commission.apply(new CommissionContext(request, commissions.comision(),
                commissions.valorComision(), request.iFrmPagcob(), posted.trn(), posted.causal(), SAVEPOINT,
                posted.tranNcnd(), posted.cadena(), servicio, terminal, sSsn));
        return switch (outcome) {
            case CommissionOutcome.ExitB b -> new DebitResult(b.oError(), b.oError(), null);       // 1698-1700, no commit
            case CommissionOutcome.LblError e -> errorExit(request, e.numError(), true);          // 1834
            case CommissionOutcome.Continue c -> {
                // B12 (1862-2090): 122004 goes to lbl_error; otherwise success (2130-2136).
                // priorRowCount is @wRowdbBiz as B12 finds it, NOT "the last @@rowcount". TODO Phase 4: set it
                // exactly where the legacy does: 824/840 and 890/912 (B7 reads), 1358 (SPI-return lookup, only
                // when actTotord is already 'N'). Until then it is the declared NULL (line 140).
                int numError = orderHeader.update(new OrderHeaderContext(request, servicio, frmPagcobDeb, actTotord,
                        posted.codErrord(), null));
                if (numError != 0) {
                    yield errorExit(request, numError, true);
                }
                tx.commit();
                yield new DebitResult(0, 0, null);
            }
        };
    }

    /** {@code @w_batch} (176-178): {@code 'S'} online, {@code 'N'} batch, anything else NULL. */
    private static Boolean batchFlag(String aplcobis) {
        if ("S".equals(aplcobis)) {
            return Boolean.FALSE;
        }
        if ("N".equals(aplcobis)) {
            return Boolean.TRUE;
        }
        return null;
    }

    /** Lines 320-374: TRANSQUICK with an SPI payment form omits the concept. RULE-030. */
    private static AccountingConfigurationQuery accountingQuery(DebitRequest r, String moneda, String conceptoBas) {
        boolean spiForm = AseText.equalsIgnoringTrailingBlanks(r.iServicio(), "TRANSQUICK") && r.iFrmPagcobSpi() != null;
        return new AccountingConfigurationQuery(r.iProducto(), r.iServicio(), r.iTipoAfec(),
                spiForm ? r.iFrmPagcobSpi() : r.iFrmPagcob(), r.iCanal(), r.iTipctaEmp(), moneda,
                r.iTipoReferencia(), r.iEmpresa(), spiForm ? null : conceptoBas);
    }

    /** {@code @i_servicio = 'SPI' and @i_tipo_afec = '12'} (558, 1348): an SPI return. */
    private static boolean isSpiReturn(DebitRequest r) {
        return AseText.trimmedEquals(r.iServicio(), "SPI") && AseText.equalsIgnoringTrailingBlanks(r.iTipoAfec(), "12");
    }

    /** The debit itself, routed by account type (RULE-008, RULE-009). */
    private Posting post(DebitRequest r, Integer sSsn, String terminal, String moneda, Integer trn, String causal,
                         Boolean batch, CommissionBreakdown commissions) {
        Integer type = r.iTipctaEmp();
        if (type == null) {
            return new Posting.Posted(UNSUPPORTED_ACCOUNT_TYPE, null, r.iValorDebito(), trn, causal, null);   // 1316
        }
        return switch (type) {
            case CURRENT_ACCOUNT, SAVINGS_ACCOUNT, VIRTUAL_ACCOUNT ->
                    postDebitNote(r, sSsn, terminal, moneda, trn, causal, batch, commissions);
            case ACCOUNTING_ACCOUNT -> postLedgerDebit(r, terminal, trn, causal);
            default -> new Posting.Posted(UNSUPPORTED_ACCOUNT_TYPE, null, r.iValorDebito(), trn, causal, null);
        };
    }

    /** Blocks B5 and B6 (414-740) for account types 3, 4 and 12. */
    private Posting postDebitNote(DebitRequest r, Integer sSsn, String terminal, String moneda, Integer trn,
                                  String causal, Boolean batch, CommissionBreakdown commissions) {
        // B5: the reference string and the special causals (RULE-038, RULE-031, RULE-032)
        String cadena = AseText.toVarchar(r.iOrdenEmpresa());                                    // 422, 448
        if (AseText.trimmedEquals(r.iServicio(), "TRANSWIFT")) {                                  // 430-440
            cadena = r.iCodSwift() == null ? null : "COD:" + r.iCodSwift();                       // NULL concatenation
        }
        if (AseText.trimmedEquals(r.iServicio(), "IMPADUAN")) {                                   // 456-550
            String codSwift = r.iCodSwift() == null ? "" : r.iCodSwift();
            if (AseText.trimmedEquals(codSwift, "CORPEI")) {
                String concepto = catalog.ndcorpeiConcept().orElse("99999");
                AccountingConfiguration corpei = accountingConfiguration.resolve(new AccountingConfigurationQuery(
                        r.iProducto(), r.iServicio(), r.iTipoAfec(), r.iFrmPagcob(), r.iCanal(), r.iTipctaEmp(),
                        moneda, r.iTipoReferencia(), r.iEmpresa(), concepto));
                trn = corpei.trn();
                causal = corpei.causal() == null ? "512" : corpei.causal();
                if (corpei.returnCode() > 0) {
                    return new Posting.Aborted(NO_ACCOUNTING_CONFIGURATION);                     // 538-542
                }
            }
        }
        if (isSpiReturn(r)) {                                                                     // 562-614 (ref39)
            cadena = AseText.toVarchar(r.iOrdenEmpresa());
        }

        // B6: the debit note (RULE-009, RULE-033)
        int codErrord;
        Integer tranNcnd;
        if (Objects.equals(r.iTipctaEmp(), VIRTUAL_ACCOUNT)) {                                    // 628-666
            VirtualDebitNoteResult result = virtualDebitNote.debit(new VirtualDebitNoteCommand(
                    r.sSrv(), 0, r.sUser(), terminal, trn, r.iNumctaEmp(), r.iValorDebito(), causal,
                    r.iMonDebito(), r.iEmpresa(), "SAT", "S", batch, cadena, r.iOrden()));
            codErrord = result.returnCode();
            tranNcnd = result.ssnMonet();
        } else {                                                                                  // 676-738
            DebitNoteResult result = debitNote.debit(new DebitNoteCommand(
                    sSsn, r.sSrv(), r.sUser(), terminal, 0, r.iFechaProceso(), trn, r.iNumctaEmp(), r.iTipctaEmp(),
                    causal, r.iValorDebito(), r.iMonDebito(), r.iTarjeta(), r.iServicio(), cadena, r.iAplcobis(),
                    r.iRefProv(), commissions.comision(), r.iCanal(), r.iFrmPagcobSpi(), r.iOrden(), r.iFrmPagcob(),
                    r.iOrden(), 0, commissions.cantidad(), commissions.comisionUnd()));
            codErrord = result.returnCode();
            tranNcnd = result.transaccion();
        }

        // B7 (746-1256): notifications, a step of Phase 4; its outcome overwrites the debit result (1248)
        BigDecimal valorDebito = r.iValorDebito();
        if (codErrord == 0 && !AseText.trimmedEquals(r.iServicio(), "PAGOPRV")) {
            NotificationOutcome outcome = notification.notify(new NotificationContext(r, r.iServicio(), trn, causal,
                    valorDebito, commissions.comision(), commissions.valorComision(), tranNcnd));
            if (outcome.configured()) {
                codErrord = outcome.returnCode();                                                 // RULE-012, RULE-034
                if (outcome.valorDebito() != null) {
                    valorDebito = outcome.valorDebito();                                          // RULE-003
                }
            }
        }
        return new Posting.Posted(codErrord, tranNcnd, valorDebito, trn, causal, cadena);
    }

    /** Block B8 (1260-1300): an accounting account; company 1295 on SPI books the amount as balance. RULE-022. */
    private Posting postLedgerDebit(DebitRequest r, String terminal, Integer trn, String causal) {
        String empresa = AseText.toVarchar(r.iEmpresa());
        BigDecimal saldo = null;
        BigDecimal valor = r.iValorDebito();
        BigDecimal valorDebito = r.iValorDebito();
        if (COMPANY_WITH_BALANCE_BOOKING.equals(empresa) && AseText.equalsIgnoringTrailingBlanks(r.iServicio(), "SPI")) {
            saldo = r.iValorDebito();                                                             // 1276-1278 (REF45)
            valor = BigDecimal.ZERO;
            valorDebito = BigDecimal.ZERO;
        }
        LedgerDebitResult result = ledgerDebit.debit(new LedgerDebitCommand(
                r.sSrv(), r.sOfi(), null, r.sUser(), terminal, trn, r.iFechaProceso(), r.iRefProv(), r.iNumctaEmp(),
                r.sOfi(), 1, r.iMonDebito(), causal, saldo, valor, 0, empresa, r.iOrden(), 0));
        return new Posting.Posted(result.returnCode(), null, valorDebito, trn, causal, null);
    }

    /** {@code WRITELOG}-equivalent movement record (1394-1460). */
    private static MovementCommand movementCommand(DebitRequest r, String terminal, Posting.Posted posted,
                                                   String stsProc, String servicio, CommissionBreakdown commissions) {
        return new MovementCommand(r.sUser(), terminal, r.sOfi(), posted.trn(), r.iTipoProceso(), r.iEmpresa(),
                r.iProducto(), r.iOrden(), r.iCanal(), posted.causal(), stsProc, posted.codErrord(), r.iFrmPagcob(),
                r.iMonDebito(), posted.valorDebito(), "1", r.iFechaProceso(), r.iReferencia(), 0, servicio,
                r.iTipoPagcob(), r.iPaisCta(), r.iCodBancoCta(), r.iTipctaEmp(), r.iNumctaEmp(), r.iValorOrdenado(),
                r.iNemEmp(), r.iLocalidadOrden(), null, null, r.iOrdenEmpresa(), commissions.comision(),
                posted.tranNcnd());
    }

    /**
     * {@code lbl_error} (2140-2170). RULE-028: with {@code @i_aplcobis = 'S'} the code is the return
     * value, {@code sp_cerror} is called and {@code @o_error} keeps the 0 set at line 256; otherwise the
     * return value is 0 and {@code @o_error} carries the code.
     */
    private DebitResult errorExit(DebitRequest r, int numError, boolean transactionOpen) {
        if (transactionOpen) {
            tx.rollback();
        }
        if (AseText.equalsIgnoringTrailingBlanks(r.iAplcobis(), "S")) {
            errorReporting.report(new ErrorReport(r.iSpName(), numError));
            return new DebitResult(numError, 0, null);
        }
        return new DebitResult(0, numError, null);
    }

    /** What blocks B5-B8 leave for B9. */
    private sealed interface Posting {

        /** The debit ran (or was refused with 122001): its code, sequence and the working values B9-B11 need ({@code cadena} = {@code @w_cadena}). */
        record Posted(int codErrord, Integer tranNcnd, BigDecimal valorDebito, Integer trn, String causal, String cadena)
                implements Posting {
        }

        /** {@code goto lbl_error} from inside the transaction (line 542). */
        record Aborted(int numError) implements Posting {
        }
    }
}
