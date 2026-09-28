package com.nexti.debcred.support;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.nexti.debcred.AccountingConfiguration;
import com.nexti.debcred.AccountingConfigurationPort;
import com.nexti.debcred.AccountingConfigurationQuery;
import com.nexti.debcred.AsePortException;
import com.nexti.debcred.AseSession;
import com.nexti.debcred.CatalogReader;
import com.nexti.debcred.CommissionCommand;
import com.nexti.debcred.CommissionContext;
import com.nexti.debcred.CommissionDebits;
import com.nexti.debcred.CommissionOutcome;
import com.nexti.debcred.CommissionPort;
import com.nexti.debcred.CommissionResult;
import com.nexti.debcred.CommissionStep;
import com.nexti.debcred.CommissionTariffPort;
import com.nexti.debcred.CommissionTariffQuery;
import com.nexti.debcred.CommissionTariffResult;
import com.nexti.debcred.DebitCompanyAccountService;
import com.nexti.debcred.DebitNoteCommand;
import com.nexti.debcred.DebitNotePort;
import com.nexti.debcred.DebitNoteResult;
import com.nexti.debcred.ErrorReport;
import com.nexti.debcred.ErrorReportingPort;
import com.nexti.debcred.LedgerDebitCommand;
import com.nexti.debcred.LedgerDebitPort;
import com.nexti.debcred.LedgerDebitResult;
import com.nexti.debcred.MovementCommand;
import com.nexti.debcred.MovementPort;
import com.nexti.debcred.MovementResult;
import com.nexti.debcred.NotificationContext;
import com.nexti.debcred.NotificationOutcome;
import com.nexti.debcred.NotificationStep;
import com.nexti.debcred.OrderHeaderContext;
import com.nexti.debcred.OrderHeaderStep;
import com.nexti.debcred.OrderReader;
import com.nexti.debcred.VirtualAccountReader;
import com.nexti.debcred.VirtualDebitNoteCommand;
import com.nexti.debcred.VirtualDebitNotePort;
import com.nexti.debcred.VirtualDebitNoteResult;

/**
 * In-memory stand-in for one Sybase ASE connection: it is the {@link AseTransaction} and every
 * port at once, so a single object records the exact sequence of transaction calls and procedure
 * calls ({@link #calls()}) and models the committed state, i.e. which writes survive after the
 * service returns ({@link #committedWrites()}).
 *
 * <p>Model: a write goes to {@code pending} while a transaction is open; {@code savepoint} marks a
 * position; {@code rollbackToSavepoint} discards the writes after that position;
 * {@code rollback} discards all pending writes; {@code commit} moves them to {@code committed}.
 * A write outside a transaction is committed at once (ASE autocommit, needed by exit B in Phase 2).
 *
 * <p>Phase 2: the session is also the {@link CommissionPort} ({@code sp_grb_comision}); the answers
 * are scripted separately for the first call ({@link #commission}) and for the SWIFT call
 * ({@link #secondCommission}, recognised by {@code iTipoafec = "16"}). {@link #service()} wires the
 * real {@link CommissionDebits} behind a thin {@link CommissionStep} that keeps logging
 * {@code "commissionStep"} and recording the {@link CommissionContext}, so the Phase 1 sequence
 * assertions still hold.
 *
 * <p>Fixture accessors such as {@link #onlyDebitNote()} throw an {@link AssertionError} when the
 * call did not happen: a test never passes because the thing it inspects is missing.
 */
public final class FakeAseSession implements AseSession, NotificationStep, OrderHeaderStep {

    public static final String SAVEPOINT = "sp_debito_empresa";

    /** One recorded write with the procedure name and its argument record. */
    public record Write(String procedure, Object payload) {
    }

    // ---- call log and committed-state model ---------------------------------------------------
    private final List<String> calls = new ArrayList<>();
    private final List<Write> pending = new ArrayList<>();
    private final List<Write> committed = new ArrayList<>();
    private final Map<String, Integer> savepoints = new HashMap<>();
    private boolean inTransaction;
    private int begins;
    private int commits;
    private int rollbacks;

    // ---- scripted answers ---------------------------------------------------------------------
    private CommissionTariffResult tariffResult = new CommissionTariffResult(0, new BigDecimal("0.50"));
    private final Deque<AccountingConfiguration> accountingResults = new ArrayDeque<>();
    private boolean accountingLookupThrows;
    private DebitNoteResult debitNoteResult = new DebitNoteResult(0, 9001);
    private VirtualDebitNoteResult virtualDebitResult = new VirtualDebitNoteResult(0, 0, 9002);
    private LedgerDebitResult ledgerDebitResult = new LedgerDebitResult(0);
    private MovementResult movementResult = new MovementResult(0);
    private MovementResult secondMovementResult;
    private boolean movementThrows;
    private Optional<String> ndcorpeiConcept = Optional.empty();
    private final Set<String> basicAccounts = new HashSet<>();
    private final Map<Integer, String> liveOrderServices = new HashMap<>();
    private final Map<Integer, String> historyOrderServices = new HashMap<>();
    private NotificationOutcome notificationOutcome = NotificationOutcome.notConfigured();
    private CommissionResult commissionResult = new CommissionResult(0, 0);
    private CommissionResult secondCommissionResult = new CommissionResult(0, 0);
    private boolean commissionThrows;
    private boolean secondCommissionThrows;

    // ---- what the ports received --------------------------------------------------------------
    private final List<CommissionTariffQuery> tariffQueries = new ArrayList<>();
    private final List<AccountingConfigurationQuery> accountingQueries = new ArrayList<>();
    private final List<DebitNoteCommand> debitNotes = new ArrayList<>();
    private final List<VirtualDebitNoteCommand> virtualDebitNotes = new ArrayList<>();
    private final List<LedgerDebitCommand> ledgerDebits = new ArrayList<>();
    private final List<MovementCommand> movements = new ArrayList<>();
    private final List<ErrorReport> errorReports = new ArrayList<>();
    private final List<NotificationContext> notifications = new ArrayList<>();
    private final List<CommissionContext> commissionSteps = new ArrayList<>();
    private final List<CommissionCommand> commissionCommands = new ArrayList<>();
    private final List<OrderHeaderContext> orderHeaderSteps = new ArrayList<>();
    private final List<String> basicAccountChecks = new ArrayList<>();
    private final List<Integer> liveOrderLookups = new ArrayList<>();
    private final List<Integer> historyOrderLookups = new ArrayList<>();
    private int catalogReads;

    public FakeAseSession() {
        accountingResults.add(new AccountingConfiguration(0, 2701, "0150"));
    }

    /**
     * The service under test wired to this session for every collaborator. The commission step is
     * the real {@link CommissionDebits} (Phase 2) over this session's commission port, movement port
     * and transaction; the wrapper only logs {@code "commissionStep"} and keeps the context.
     */
    public DebitCompanyAccountService service() {
        CommissionDebits debits = new CommissionDebits(this, this, this);
        CommissionStep logged = ctx -> {
            calls.add("commissionStep");
            commissionSteps.add(ctx);
            return debits.apply(ctx);
        };
        return new DebitCompanyAccountService(this, this, logged, this);
    }

    // ---- scripting -----------------------------------------------------------------------------

    public FakeAseSession tariff(int returnCode, String valorPorTran) {
        this.tariffResult = new CommissionTariffResult(returnCode,
                valorPorTran == null ? null : new BigDecimal(valorPorTran));
        return this;
    }

    /** Replaces the scripted accounting answers; several calls queue several answers (CORPEI). */
    public FakeAseSession accounting(AccountingConfiguration first, AccountingConfiguration... more) {
        accountingResults.clear();
        accountingResults.add(first);
        for (AccountingConfiguration c : more) {
            accountingResults.add(c);
        }
        return this;
    }

    public FakeAseSession accountingLookupThrows() {
        this.accountingLookupThrows = true;
        return this;
    }

    public FakeAseSession debitNote(int returnCode, Integer transaccion) {
        this.debitNoteResult = new DebitNoteResult(returnCode, transaccion);
        return this;
    }

    public FakeAseSession virtualDebit(int returnCode, Integer error, Integer ssnMonet) {
        this.virtualDebitResult = new VirtualDebitNoteResult(returnCode, error, ssnMonet);
        return this;
    }

    public FakeAseSession ledgerDebit(int returnCode) {
        this.ledgerDebitResult = new LedgerDebitResult(returnCode);
        return this;
    }

    public FakeAseSession movement(int returnCode) {
        this.movementResult = new MovementResult(returnCode);
        return this;
    }

    public FakeAseSession movementThrows() {
        this.movementThrows = true;
        return this;
    }

    /** Answer of the SECOND sp_grb_mov_y_frmpgo call, i.e. the exit-B 'X' movement (line 1628); the first keeps {@link #movement}. */
    public FakeAseSession secondMovement(int returnCode) {
        this.secondMovementResult = new MovementResult(returnCode);
        return this;
    }

    public FakeAseSession ndcorpeiConcept(String code) {
        this.ndcorpeiConcept = Optional.ofNullable(code);
        return this;
    }

    public FakeAseSession basicAccount(String ctaBanco) {
        this.basicAccounts.add(ctaBanco);
        return this;
    }

    public FakeAseSession liveOrder(int ordenBanco, String servicio) {
        this.liveOrderServices.put(ordenBanco, servicio);
        return this;
    }

    public FakeAseSession historyOrder(int ordenBanco, String servicio) {
        this.historyOrderServices.put(ordenBanco, servicio);
        return this;
    }

    public FakeAseSession notification(NotificationOutcome outcome) {
        this.notificationOutcome = outcome;
        return this;
    }

    /** Answer of the first {@code sp_grb_comision} call (line 1518): return value and {@code @o_error}. */
    public FakeAseSession commission(int returnCode, Integer oError) {
        this.commissionResult = new CommissionResult(returnCode, oError);
        return this;
    }

    /** Answer of the second, SWIFT {@code sp_grb_comision} call (line 1746, {@code @i_tipoafec = '16'}). */
    public FakeAseSession secondCommission(int returnCode, Integer oError) {
        this.secondCommissionResult = new CommissionResult(returnCode, oError);
        return this;
    }

    /** The first {@code sp_grb_comision} call raises a SQL error ({@code @@error <> 0}, line 1606). */
    public FakeAseSession commissionThrows() {
        this.commissionThrows = true;
        return this;
    }

    /** The SWIFT {@code sp_grb_comision} call raises a SQL error ({@code @@error <> 0}, line 1824). */
    public FakeAseSession secondCommissionThrows() {
        this.secondCommissionThrows = true;
        return this;
    }

    // ---- AseTransaction ------------------------------------------------------------------------

    @Override
    public void begin() {
        calls.add("begin");
        inTransaction = true;
        begins++;
        pending.clear();
        savepoints.clear();
    }

    @Override
    public void savepoint(String name) {
        calls.add("savepoint:" + name);
        if (!inTransaction) {
            throw new IllegalStateException("save tran outside a transaction");
        }
        savepoints.put(name, pending.size());
    }

    @Override
    public void rollbackToSavepoint(String name) {
        calls.add("rollbackToSavepoint:" + name);
        Integer position = savepoints.get(name);
        if (position == null) {
            throw new IllegalStateException("unknown savepoint " + name);
        }
        pending.subList(position, pending.size()).clear();
    }

    @Override
    public void rollback() {
        calls.add("rollback");
        if (!inTransaction) {
            throw new IllegalStateException("rollback tran with @@trancount = 0");
        }
        pending.clear();
        inTransaction = false;
        rollbacks++;
    }

    @Override
    public void commit() {
        calls.add("commit");
        if (!inTransaction) {
            throw new IllegalStateException("commit tran with @@trancount = 0");
        }
        committed.addAll(pending);
        pending.clear();
        inTransaction = false;
        commits++;
    }

    private void write(String procedure, Object payload) {
        if (inTransaction) {
            pending.add(new Write(procedure, payload));
        } else {
            committed.add(new Write(procedure, payload));
        }
    }

    // ---- ports ---------------------------------------------------------------------------------

    @Override
    public CommissionTariffResult consult(CommissionTariffQuery q) {
        calls.add("sp_con_comision");
        tariffQueries.add(q);
        return tariffResult;
    }

    @Override
    public AccountingConfiguration resolve(AccountingConfigurationQuery q) {
        calls.add("sp_con_confcontable");
        accountingQueries.add(q);
        if (accountingLookupThrows) {
            throw new AsePortException("simulated @@error on sp_con_confcontable");
        }
        if (accountingResults.isEmpty()) {
            throw new AssertionError("sp_con_confcontable called more times than the test scripted");
        }
        return accountingResults.size() == 1 ? accountingResults.peek() : accountingResults.poll();
    }

    // The three debit procedures ALWAYS record a write, even when they return an error: a real
    // procedure may have inserted rows before failing, and it is exactly the savepoint rollback of
    // exit A (line 1338) that must make those rows disappear. A test that sees a debit write in
    // committedWrites() after a failed debit has caught a broken savepoint.

    @Override
    public DebitNoteResult debit(DebitNoteCommand c) {
        calls.add("sp_ndc_ahcc");
        debitNotes.add(c);
        write("sp_ndc_ahcc", c);
        return debitNoteResult;
    }

    @Override
    public VirtualDebitNoteResult debit(VirtualDebitNoteCommand c) {
        calls.add("sp_vi_ndc_automatica");
        virtualDebitNotes.add(c);
        write("sp_vi_ndc_automatica", c);
        return virtualDebitResult;
    }

    @Override
    public LedgerDebitResult debit(LedgerDebitCommand c) {
        calls.add("sp_graba_tran_servicio");
        ledgerDebits.add(c);
        write("sp_graba_tran_servicio", c);
        return ledgerDebitResult;
    }

    @Override
    public MovementResult record(MovementCommand c) {
        calls.add("sp_grb_mov_y_frmpgo");
        movements.add(c);
        if (movementThrows) {
            throw new AsePortException("simulated @@error on sp_grb_mov_y_frmpgo");
        }
        MovementResult result = movements.size() >= 2 && secondMovementResult != null ? secondMovementResult : movementResult;
        if (result.returnCode() == 0) {
            write("sp_grb_mov_y_frmpgo", c);
        }
        return result;
    }

    @Override
    public void report(ErrorReport r) {
        calls.add("sp_cerror");
        errorReports.add(r);
    }

    @Override
    public NotificationOutcome notify(NotificationContext ctx) {
        calls.add("notificationStep");
        notifications.add(ctx);
        return notificationOutcome;
    }

    // sp_grb_comision debits the commission and records its own movement: like the three debit
    // procedures it ALWAYS leaves a write, so exit B's full rollback (line 1624) is seen to discard it.
    @Override
    public CommissionResult charge(CommissionCommand c) {
        calls.add("sp_grb_comision");
        commissionCommands.add(c);
        write("sp_grb_comision", c);
        boolean swift = "16".equals(c.iTipoafec());
        if (swift ? secondCommissionThrows : commissionThrows) {
            throw new AsePortException("simulated @@error on sp_grb_comision");
        }
        return swift ? secondCommissionResult : commissionResult;
    }

    @Override
    public int update(OrderHeaderContext ctx) {
        calls.add("orderHeaderStep");
        orderHeaderSteps.add(ctx);
        return 0;
    }

    @Override
    public Optional<String> ndcorpeiConcept() {
        calls.add("ba_catalogo:NDCORPEI");
        catalogReads++;
        return ndcorpeiConcept;
    }

    @Override
    public boolean isBasicAccount(String ctaBanco) {
        calls.add("vi_cuenta:" + ctaBanco);
        basicAccountChecks.add(ctaBanco);
        return basicAccounts.contains(ctaBanco);
    }

    @Override
    public Optional<String> liveService(Integer ordenBanco) {
        calls.add("bp_orden:" + ordenBanco);
        liveOrderLookups.add(ordenBanco);
        return Optional.ofNullable(liveOrderServices.get(ordenBanco));
    }

    @Override
    public Optional<String> historyService(Integer ordenBanco) {
        calls.add("bp_orden_his:" + ordenBanco);
        historyOrderLookups.add(ordenBanco);
        return Optional.ofNullable(historyOrderServices.get(ordenBanco));
    }

    // ---- observations --------------------------------------------------------------------------

    public List<String> calls() {
        return List.copyOf(calls);
    }

    public List<Write> committedWrites() {
        return List.copyOf(committed);
    }

    public List<String> committedProcedures() {
        return committed.stream().map(Write::procedure).toList();
    }

    public boolean transactionOpen() {
        return inTransaction;
    }

    public int begins() {
        return begins;
    }

    public int commits() {
        return commits;
    }

    public int rollbacks() {
        return rollbacks;
    }

    public List<CommissionTariffQuery> tariffQueries() {
        return List.copyOf(tariffQueries);
    }

    public CommissionTariffQuery onlyTariffQuery() {
        return only(tariffQueries, "sp_con_comision");
    }

    public List<AccountingConfigurationQuery> accountingQueries() {
        return List.copyOf(accountingQueries);
    }

    public AccountingConfigurationQuery onlyAccountingQuery() {
        return only(accountingQueries, "sp_con_confcontable");
    }

    public List<DebitNoteCommand> debitNotes() {
        return List.copyOf(debitNotes);
    }

    public DebitNoteCommand onlyDebitNote() {
        return only(debitNotes, "sp_ndc_ahcc");
    }

    public List<VirtualDebitNoteCommand> virtualDebitNotes() {
        return List.copyOf(virtualDebitNotes);
    }

    public VirtualDebitNoteCommand onlyVirtualDebitNote() {
        return only(virtualDebitNotes, "sp_vi_ndc_automatica");
    }

    public List<LedgerDebitCommand> ledgerDebits() {
        return List.copyOf(ledgerDebits);
    }

    public LedgerDebitCommand onlyLedgerDebit() {
        return only(ledgerDebits, "sp_graba_tran_servicio");
    }

    public List<MovementCommand> movements() {
        return List.copyOf(movements);
    }

    public MovementCommand onlyMovement() {
        return only(movements, "sp_grb_mov_y_frmpgo");
    }

    public List<ErrorReport> errorReports() {
        return List.copyOf(errorReports);
    }

    public ErrorReport onlyErrorReport() {
        return only(errorReports, "sp_cerror");
    }

    public List<NotificationContext> notifications() {
        return List.copyOf(notifications);
    }

    public NotificationContext onlyNotification() {
        return only(notifications, "notificationStep");
    }

    public List<CommissionContext> commissionSteps() {
        return List.copyOf(commissionSteps);
    }

    public CommissionContext onlyCommissionStep() {
        return only(commissionSteps, "commissionStep");
    }

    public List<CommissionCommand> commissionCommands() {
        return List.copyOf(commissionCommands);
    }

    public CommissionCommand onlyCommissionCommand() {
        return only(commissionCommands, "sp_grb_comision");
    }

    /** The two movements of exit B (the Phase 1 'P' one, then the 'X' one written after the rollback). */
    public MovementCommand lastMovement() {
        if (movements.isEmpty()) {
            throw new AssertionError("expected at least one call to sp_grb_mov_y_frmpgo but recorded none");
        }
        return movements.get(movements.size() - 1);
    }

    public List<OrderHeaderContext> orderHeaderSteps() {
        return List.copyOf(orderHeaderSteps);
    }

    public OrderHeaderContext onlyOrderHeaderStep() {
        return only(orderHeaderSteps, "orderHeaderStep");
    }

    public List<String> basicAccountChecks() {
        return List.copyOf(basicAccountChecks);
    }

    public List<Integer> liveOrderLookups() {
        return List.copyOf(liveOrderLookups);
    }

    public List<Integer> historyOrderLookups() {
        return List.copyOf(historyOrderLookups);
    }

    public int catalogReads() {
        return catalogReads;
    }

    private static <T> T only(List<T> received, String what) {
        if (received.size() != 1) {
            throw new AssertionError("expected exactly one call to " + what + " but recorded " + received.size()
                    + ": " + received);
        }
        return received.get(0);
    }
}
