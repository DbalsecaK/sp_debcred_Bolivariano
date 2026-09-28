package com.nexti.debcred.support;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.nexti.debcred.AccountingConfiguration;
import com.nexti.debcred.AccountingConfigurationPort;
import com.nexti.debcred.AccountingConfigurationQuery;
import com.nexti.debcred.AsePortException;
import com.nexti.debcred.AseSession;
import com.nexti.debcred.BasicNotificationCommand;
import com.nexti.debcred.BasicNotificationResult;
import com.nexti.debcred.BeneficiaryDetail;
import com.nexti.debcred.CatalogReader;
import com.nexti.debcred.CustomerNotifications;
import com.nexti.debcred.EventCommand;
import com.nexti.debcred.EventResult;
import com.nexti.debcred.InterbankCreditDetail;
import com.nexti.debcred.SwiftCreditDetail;
import com.nexti.debcred.VirtualAccountOwner;
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
import com.nexti.debcred.OrderHeaderRepository;
import com.nexti.debcred.OrderHeaderStep;
import com.nexti.debcred.OrderHeaderTransition;
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
 * <p>Phase 3: the session is also the {@link OrderHeaderRepository} ({@code bp_total_orden} and
 * {@code db_sat_his..bp_total_orden_his}, lines 1886-1920). Each table is an in-memory list of header
 * rows (order, payment form, service, state, error code) scripted with {@link #headerRow} /
 * {@link #historyHeaderRow}. An update applies the legacy WHERE clause with Sybase semantics
 * (trailing blanks ignored, NULL never equal, {@code isnull(state,'I') = 'I'}) and returns
 * {@code @@rowcount}. The row changes follow the transaction model: {@code begin} and
 * {@code savepoint} snapshot the tables, {@code rollback}/{@code rollbackToSavepoint} restore the
 * snapshot, {@code commit} keeps the changes; outside a transaction they apply at once. The header
 * updates are table UPDATEs, not procedure calls: they are logged in {@link #headerUpdates()} and
 * NOT in {@link #calls()} / {@link #committedWrites()}, which keep meaning "procedure calls" and
 * "procedure writes" exactly as in Phase 1/2. The committed result is read with
 * {@link #headerState}/{@link #historyHeaderState} after the service returned.
 *
 * <p>A session whose header tables were never scripted (every Phase 1/2 test, which predate B12)
 * behaves as if the order header existed in state I for whatever the step asks: the live update
 * answers 1 row and changes nothing. Phase 3 tests always script rows or call
 * {@link #noHeaderRows()}. {@link #service()} wires the real {@link OrderHeaderTransition} behind a
 * thin wrapper that keeps logging {@code "orderHeaderStep"} and recording the context.
 *
 * <p>Phase 4: the session is also the five B7 ports (lines 746-1256): the catalogue reads
 * ({@code ad_servicios_sms}, {@code ad_notificacion_basica}, {@code ba_bloqueaNotificacionSAT},
 * {@code ad_cuentas_bce}), the order-detail reads (live {@code bp_orden}/{@code bp_detalle}, history
 * {@code db_sat_his}), the debtor account masters and the two notifiers ({@code pa_sat_pnotificacion},
 * {@code cob_internet..sp_eventos}). The reads apply the legacy WHERE clauses over in-memory rows
 * (Sybase {@code =}, LIKE with {@code %}/{@code _}, NULL never equal) and are logged in order in
 * {@link #notificationLog()}, NOT in {@link #calls()}. The two notifiers are procedure calls: they go to
 * {@link #calls()} and always record a write, so the savepoint rollback of exit A and the full rollbacks
 * of exits B and C are seen to discard them. {@link #service()} wires the real
 * {@link CustomerNotifications} behind the logging {@code "notificationStep"}; {@link #notification}
 * still scripts the whole step (the Phase 1 stub tests). With an empty catalogue the real step answers
 * "not configured", i.e. exactly the Phase 1 stub.
 *
 * <p>Fixture accessors such as {@link #onlyDebitNote()} throw an {@link AssertionError} when the
 * call did not happen: a test never passes because the thing it inspects is missing.
 */
public final class FakeAseSession implements AseSession, NotificationStep {

    public static final String SAVEPOINT = "sp_debito_empresa";

    /** The two order-header tables of B12. */
    public enum HeaderTable { LIVE, HISTORY }

    /** One order-header row ({@code te_orden_banco, te_frm_pagcob, te_servicio, te_estado_proceso, te_codigo_error}). */
    public record HeaderRow(Integer order, String form, String service, String state, Integer codError) {
    }

    /** One UPDATE the step issued: table, the WHERE arguments, the code written, {@code @@rowcount} ({@code -1} = it raised {@code @@error}). */
    public record HeaderUpdate(HeaderTable table, Integer ordenBanco, List<String> paymentForms, String servicio,
                               int codError, int rowCount) {
    }

    /** One recorded write with the procedure name and its argument record. */
    public record Write(String procedure, Object payload) {
    }

    /** The two order-detail sources of B7: {@code db_biz_pagos} (and the home database) live, {@code db_sat_his} history. */
    public enum DetailTable { LIVE, HISTORY }

    /** One {@code ba_catalogo} row of the {@code ba_tabla} named {@code table}. */
    public record CatalogRow(String table, String code, String name, String otherField, String state) {
    }

    /**
     * One {@code bp_detalle} row with the {@code or_ordenante} of its {@code bp_orden} header folded in
     * ({@code dt_orden_banco, dt_secuencial, or_ordenante, dt_referencia_grupo, dt_nom_cuenta, dt_tipo_cta,
     * dt_numero_cuenta, dt_nombre_beneficiario}).
     */
    public record DetailRow(Integer order, Integer secuencial, Integer ordenante, String referenciaGrupo,
                            String nomCuenta, Integer tipoCta, String numeroCuenta, String nombreBeneficiario) {
    }

    /** Arguments of one {@code ad_servicios_sms} read. */
    public record SmsQuery(String servicio, String canalSms) {
    }

    /** Arguments of one {@code ba_bloqueaNotificacionSAT} read. */
    public record BlockQuery(String servicio, String servicioSms, String spName) {
    }

    /** Arguments of one TRANSWIFT detail read. */
    public record SwiftQuery(DetailTable table, Integer ordenBanco, Integer secuencial, Integer ordenante) {
    }

    /** Arguments of one detail read keyed by order only (interbank join, beneficiary). */
    public record OrderQuery(DetailTable table, Integer ordenBanco) {
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
    /** null = run the real {@link CustomerNotifications} (Phase 4); non-null = the scripted stub answer (Phase 1). */
    private NotificationOutcome notificationOutcome;
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

    // ---- Phase 3: order-header tables ----------------------------------------------------------
    private final Map<HeaderTable, List<HeaderRow>> headerTables = new HashMap<>(Map.of(
            HeaderTable.LIVE, new ArrayList<>(), HeaderTable.HISTORY, new ArrayList<>()));
    private boolean headersScripted;
    private final Set<HeaderTable> headerUpdateThrows = new HashSet<>();
    private final List<HeaderUpdate> headerUpdates = new ArrayList<>();
    private Map<HeaderTable, List<HeaderRow>> headerSnapshotAtBegin;
    private final Map<String, Map<HeaderTable, List<HeaderRow>>> headerSnapshotAtSavepoint = new HashMap<>();

    // ---- Phase 4: B7 tables, notifier answers and what the B7 ports received --------------------
    private final List<CatalogRow> catalogRows = new ArrayList<>();
    private final Map<String, String> catalogTableStates = new HashMap<>();
    private final Map<DetailTable, List<DetailRow>> detailTables = new HashMap<>(Map.of(
            DetailTable.LIVE, new ArrayList<>(), DetailTable.HISTORY, new ArrayList<>()));
    private final Map<String, Integer> currentOwners = new HashMap<>();
    private final Map<String, Integer> savingsOwners = new HashMap<>();
    private final Map<String, VirtualAccountOwner> virtualOwners = new HashMap<>();
    private BasicNotificationResult basicNotificationResult = new BasicNotificationResult(0, 0, null);
    private EventResult eventResult = new EventResult(0);
    private final List<String> notificationLog = new ArrayList<>();
    private final List<SmsQuery> smsQueries = new ArrayList<>();
    private final List<String> classQueries = new ArrayList<>();
    private final List<BlockQuery> blockQueries = new ArrayList<>();
    private final List<SwiftQuery> swiftQueries = new ArrayList<>();
    private final List<OrderQuery> interbankQueries = new ArrayList<>();
    private final List<OrderQuery> beneficiaryQueries = new ArrayList<>();
    private final List<String> ownerQueries = new ArrayList<>();
    private final List<BasicNotificationCommand> basicNotifications = new ArrayList<>();
    private final List<EventCommand> events = new ArrayList<>();
    private int catalogCodes;
    private CustomerNotifications customerNotifications;

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
        OrderHeaderTransition transition = new OrderHeaderTransition(this);
        OrderHeaderStep loggedHeader = ctx -> {
            calls.add("orderHeaderStep");
            orderHeaderSteps.add(ctx);
            return transition.update(ctx);
        };
        return new DebitCompanyAccountService(this, this, logged, loggedHeader);
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

    /** Scripts the WHOLE notification step (the Phase 1 stub): the real {@link CustomerNotifications} is then not run. */
    public FakeAseSession notification(NotificationOutcome outcome) {
        this.notificationOutcome = outcome;
        return this;
    }

    // ---- Phase 4 scripting ---------------------------------------------------------------------

    /** A {@code ba_catalogo} row of the {@code ba_tabla} named {@code table}; {@code state} is {@code ct_est_catalogo}. */
    public FakeAseSession catalogRow(String table, String code, String name, String otherField, String state) {
        catalogRows.add(new CatalogRow(table, code, name, otherField, state));
        return this;
    }

    /** {@code tb_est_tabla} of a {@code ba_tabla} row; every table is {@code 'A'} unless scripted. */
    public FakeAseSession catalogTableState(String table, String state) {
        catalogTableStates.put(table, state);
        return this;
    }

    /** An active {@code ad_servicios_sms} row, e.g. {@code ("TRC-SAT", "NOTIFICA TRANSCLI")}. */
    public FakeAseSession smsService(String code, String name) {
        return catalogRow("ad_servicios_sms", code, name, null, "A");
    }

    /** An active {@code ad_notificacion_basica} row: {@code ct_cod_catalogo = service}, {@code ct_otro_campo_catalogo = value}. */
    public FakeAseSession notificationClassRow(String service, String value) {
        return catalogRow("ad_notificacion_basica", service, "CLASE " + service, value, "A");
    }

    /** An active {@code ba_bloqueaNotificacionSAT} row: {@code ct_nom_catalogo = key}, {@code ct_otro_campo_catalogo = spName}. */
    public FakeAseSession blockedNotification(String key, String spName) {
        return catalogRow("ba_bloqueaNotificacionSAT", "BLQ" + (++catalogCodes), key, spName, "A");
    }

    /** An active {@code ad_cuentas_bce} row: {@code ct_nom_catalogo} = 9-character group reference + institution name. */
    public FakeAseSession bceInstitution(String name) {
        return catalogRow("ad_cuentas_bce", "BCE" + (++catalogCodes), name, null, "A");
    }

    public FakeAseSession detailRow(DetailTable table, DetailRow row) {
        detailTables.get(table).add(row);
        return this;
    }

    /** A detail row the TRANSWIFT read (826-856) can match: order, sequence, ordering company, group reference, account name. */
    public FakeAseSession swiftDetail(DetailTable table, int order, int secuencial, int ordenante, String referenciaGrupo,
                                      String nomCuenta) {
        return detailRow(table, new DetailRow(order, secuencial, ordenante, referenciaGrupo, nomCuenta, null, null, null));
    }

    /** A detail row the interbank join (892-932) can match through {@code ad_cuentas_bce}. */
    public FakeAseSession interbankDetail(DetailTable table, int order, String referenciaGrupo, Integer tipoCta,
                                          String numeroCuenta) {
        return detailRow(table, new DetailRow(order, null, null, referenciaGrupo, null, tipoCta, numeroCuenta, null));
    }

    /** A detail row for the 'B' notification (1024-1044): beneficiary name and group reference. */
    public FakeAseSession beneficiaryDetail(DetailTable table, int order, String nombreBeneficiario, String referenciaGrupo) {
        return detailRow(table, new DetailRow(order, null, null, referenciaGrupo, null, null, null, nombreBeneficiario));
    }

    /** {@code cob_cuentas..cc_ctacte}: account -> {@code cc_cliente} (may be NULL). */
    public FakeAseSession currentOwner(String ctaBanco, Integer cliente) {
        currentOwners.put(stripBlanks(ctaBanco), cliente);
        return this;
    }

    /** {@code cob_ahorros..ah_cuenta}: account -> {@code ah_cliente} (may be NULL). */
    public FakeAseSession savingsOwner(String ctaBanco, Integer cliente) {
        savingsOwners.put(stripBlanks(ctaBanco), cliente);
        return this;
    }

    /** {@code cob_virtuales..vi_cuenta}: account -> {@code vi_cliente}, {@code vi_prod_banc}. */
    public FakeAseSession virtualOwner(String ctaBanco, Integer cliente, Integer prodBanc) {
        virtualOwners.put(stripBlanks(ctaBanco), new VirtualAccountOwner(cliente, prodBanc));
        return this;
    }

    /** Answer of {@code pa_sat_pnotificacion}: return value, {@code @o_error} output, {@code @o_msg}. */
    public FakeAseSession basicNotification(int returnCode, Integer oError, String oMsg) {
        this.basicNotificationResult = new BasicNotificationResult(returnCode, oError, oMsg);
        return this;
    }

    /** Return value of {@code cob_internet..sp_eventos}. */
    public FakeAseSession event(int returnCode) {
        this.eventResult = new EventResult(returnCode);
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

    /** A row of {@code bp_total_orden}; {@code state} null is a NULL {@code te_estado_proceso}. */
    public FakeAseSession headerRow(int order, String form, String service, String state) {
        headersScripted = true;
        headerTables.get(HeaderTable.LIVE).add(new HeaderRow(order, form, service, state, null));
        return this;
    }

    /** A row of {@code db_sat_his..bp_total_orden_his}. */
    public FakeAseSession historyHeaderRow(int order, String form, String service, String state) {
        headersScripted = true;
        headerTables.get(HeaderTable.HISTORY).add(new HeaderRow(order, form, service, state, null));
        return this;
    }

    /** Both header tables exist and are empty: every update answers 0 rows. */
    public FakeAseSession noHeaderRows() {
        headersScripted = true;
        return this;
    }

    /** The UPDATE on that table raises a SQL error ({@code @@error <> 0}, never checked by the legacy). */
    public FakeAseSession headerUpdateThrows(HeaderTable table) {
        headerUpdateThrows.add(table);
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
        headerSnapshotAtBegin = copyHeaders();
        headerSnapshotAtSavepoint.clear();
    }

    @Override
    public void savepoint(String name) {
        calls.add("savepoint:" + name);
        if (!inTransaction) {
            throw new IllegalStateException("save tran outside a transaction");
        }
        savepoints.put(name, pending.size());
        headerSnapshotAtSavepoint.put(name, copyHeaders());
    }

    @Override
    public void rollbackToSavepoint(String name) {
        calls.add("rollbackToSavepoint:" + name);
        Integer position = savepoints.get(name);
        if (position == null) {
            throw new IllegalStateException("unknown savepoint " + name);
        }
        pending.subList(position, pending.size()).clear();
        restoreHeaders(headerSnapshotAtSavepoint.get(name));
    }

    @Override
    public void rollback() {
        calls.add("rollback");
        if (!inTransaction) {
            throw new IllegalStateException("rollback tran with @@trancount = 0");
        }
        pending.clear();
        restoreHeaders(headerSnapshotAtBegin);
        headerSnapshotAtBegin = null;
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
        headerSnapshotAtBegin = null;
        headerSnapshotAtSavepoint.clear();
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

    /** The logging step {@link #service()} wires: scripted answer if any, else the real Phase 4 step over this session. */
    @Override
    public NotificationOutcome notify(NotificationContext ctx) {
        calls.add("notificationStep");
        notifications.add(ctx);
        if (notificationOutcome != null) {
            return notificationOutcome;
        }
        if (customerNotifications == null) {
            customerNotifications = new CustomerNotifications(this, this, this, this, this);
        }
        return customerNotifications.notify(ctx);
    }

    // ---- Phase 4 ports: catalogue (798-810, 998-1010, 1166-1180) -------------------------------

    @Override
    public List<String> smsServiceCodes(String servicio, String canalSms) {
        notificationLog.add("ad_servicios_sms");
        smsQueries.add(new SmsQuery(servicio, canalSms));
        // like '%' + ltrim(rtrim(@i_servicio)) + '%': ASE concatenation treats a NULL as empty, so a step that
        // forgot the entry guard would see every row here and the guard tests would catch it.
        String trimmed = aseTrim(servicio);
        Pattern like = like("%" + (trimmed == null ? "" : trimmed) + "%");
        return catalogRows.stream()
                .filter(r -> "ad_servicios_sms".equals(r.table()))
                .filter(r -> aseEquals(r.state(), "A"))
                .filter(r -> r.name() != null && like.matcher(r.name()).matches())
                .filter(r -> {
                    String code = aseTrim(r.code());
                    return code != null && aseEquals(code.substring(Math.max(0, code.length() - 3)), canalSms);
                })
                .map(CatalogRow::code)
                .sorted()
                .toList();
    }

    @Override
    public Optional<String> notificationClass(String servicio) {
        notificationLog.add("ad_notificacion_basica");
        classQueries.add(servicio);
        if (!tableActive("ad_notificacion_basica")) {
            return Optional.empty();
        }
        return catalogRows.stream()
                .filter(r -> "ad_notificacion_basica".equals(r.table()))
                .filter(r -> aseEquals(r.state(), "A") && aseEquals(r.code(), servicio))
                .map(CatalogRow::otherField)
                .filter(Objects::nonNull)
                .findFirst();
    }

    @Override
    public boolean notificationBlocked(String servicio, String servicioSms, String spName) {
        notificationLog.add("ba_bloqueaNotificacionSAT");
        blockQueries.add(new BlockQuery(servicio, servicioSms, spName));
        String sp = aseRtrim(spName);                       // rtrim(NULL or blanks) is NULL: '...' = NULL is never true
        if (sp == null) {
            return false;
        }
        String key = nullAsEmpty(aseRtrim(servicio)) + "-" + nullAsEmpty(aseRtrim(servicioSms));
        return catalogRows.stream()
                .filter(r -> "ba_bloqueaNotificacionSAT".equals(r.table()))
                .filter(r -> aseEquals(r.state(), "A"))
                .anyMatch(r -> aseEquals(nullAsEmpty(r.name()), key) && aseEquals(nullAsEmpty(r.otherField()), sp));
    }

    // ---- Phase 4 ports: order detail (826-856, 892-932, 1024-1044) ------------------------------

    @Override
    public List<SwiftCreditDetail> liveSwiftCreditDetails(Integer ordenBanco, Integer secuencial, Integer ordenante) {
        return swiftDetails(DetailTable.LIVE, ordenBanco, secuencial, ordenante);
    }

    @Override
    public List<SwiftCreditDetail> historySwiftCreditDetails(Integer ordenBanco, Integer secuencial, Integer ordenante) {
        return swiftDetails(DetailTable.HISTORY, ordenBanco, secuencial, ordenante);
    }

    private List<SwiftCreditDetail> swiftDetails(DetailTable table, Integer ordenBanco, Integer secuencial, Integer ordenante) {
        notificationLog.add(table == DetailTable.LIVE ? "bp_detalle:swift" : "bp_detalle_his:swift");
        swiftQueries.add(new SwiftQuery(table, ordenBanco, secuencial, ordenante));
        return detailTables.get(table).stream()
                .filter(r -> sameNonNull(r.order(), ordenBanco) && sameNonNull(r.secuencial(), secuencial)
                        && sameNonNull(r.ordenante(), ordenante))
                .map(r -> new SwiftCreditDetail(r.referenciaGrupo(), r.nomCuenta()))
                .toList();
    }

    @Override
    public List<InterbankCreditDetail> liveInterbankCreditDetails(Integer ordenBanco) {
        return interbankDetails(DetailTable.LIVE, ordenBanco);
    }

    @Override
    public List<InterbankCreditDetail> historyInterbankCreditDetails(Integer ordenBanco) {
        return interbankDetails(DetailTable.HISTORY, ordenBanco);
    }

    private List<InterbankCreditDetail> interbankDetails(DetailTable table, Integer ordenBanco) {
        notificationLog.add(table == DetailTable.LIVE ? "bp_detalle:ad_cuentas_bce" : "bp_detalle_his:ad_cuentas_bce");
        interbankQueries.add(new OrderQuery(table, ordenBanco));
        List<InterbankCreditDetail> rows = new ArrayList<>();
        if (!tableActive("ad_cuentas_bce")) {
            return rows;
        }
        for (DetailRow d : detailTables.get(table)) {
            if (!sameNonNull(d.order(), ordenBanco)) {
                continue;
            }
            for (CatalogRow c : catalogRows) {
                if ("ad_cuentas_bce".equals(c.table()) && aseEquals(c.state(), "A") && c.name() != null
                        && aseEquals(d.referenciaGrupo(), c.name().substring(0, Math.min(9, c.name().length())))) {
                    rows.add(new InterbankCreditDetail(c.name(), d.tipoCta(), d.numeroCuenta()));
                }
            }
        }
        return rows;
    }

    @Override
    public List<BeneficiaryDetail> liveBeneficiaryDetails(Integer ordenBanco) {
        return beneficiaryDetails(DetailTable.LIVE, ordenBanco);
    }

    @Override
    public List<BeneficiaryDetail> historyBeneficiaryDetails(Integer ordenBanco) {
        return beneficiaryDetails(DetailTable.HISTORY, ordenBanco);
    }

    private List<BeneficiaryDetail> beneficiaryDetails(DetailTable table, Integer ordenBanco) {
        notificationLog.add(table == DetailTable.LIVE ? "bp_detalle:beneficiary" : "bp_detalle_his:beneficiary");
        beneficiaryQueries.add(new OrderQuery(table, ordenBanco));
        return detailTables.get(table).stream()
                .filter(r -> sameNonNull(r.order(), ordenBanco))
                .map(r -> new BeneficiaryDetail(r.nombreBeneficiario(), r.referenciaGrupo()))
                .toList();
    }

    // ---- Phase 4 ports: debtor account masters (1110-1152) --------------------------------------

    @Override
    public Optional<Integer> currentAccountClient(String ctaBanco) {
        notificationLog.add("cc_ctacte");
        ownerQueries.add("cc_ctacte:" + ctaBanco);
        return ctaBanco == null ? Optional.empty() : Optional.ofNullable(currentOwners.get(stripBlanks(ctaBanco)));
    }

    @Override
    public Optional<Integer> savingsAccountClient(String ctaBanco) {
        notificationLog.add("ah_cuenta");
        ownerQueries.add("ah_cuenta:" + ctaBanco);
        return ctaBanco == null ? Optional.empty() : Optional.ofNullable(savingsOwners.get(stripBlanks(ctaBanco)));
    }

    @Override
    public Optional<VirtualAccountOwner> virtualAccountOwner(String ctaBanco) {
        notificationLog.add("vi_cuenta");
        ownerQueries.add("vi_cuenta:" + ctaBanco);
        return ctaBanco == null ? Optional.empty() : Optional.ofNullable(virtualOwners.get(stripBlanks(ctaBanco)));
    }

    // ---- Phase 4 ports: the two notifiers (1052-1082, 1210-1242) --------------------------------
    // Like every procedure of this fake they ALWAYS leave a write: exit A's savepoint rollback (1338)
    // and the full rollbacks of exits B/C must be seen to discard the notification.

    @Override
    public BasicNotificationResult notifyBasic(BasicNotificationCommand c) {
        calls.add("pa_sat_pnotificacion");
        notificationLog.add("pa_sat_pnotificacion");
        basicNotifications.add(c);
        write("pa_sat_pnotificacion", c);
        return basicNotificationResult;
    }

    @Override
    public EventResult registerEvent(EventCommand c) {
        calls.add("sp_eventos");
        notificationLog.add("sp_eventos");
        events.add(c);
        write("sp_eventos", c);
        return eventResult;
    }

    private boolean tableActive(String table) {
        return aseEquals(catalogTableStates.getOrDefault(table, "A"), "A");
    }

    private static boolean sameNonNull(Integer a, Integer b) {
        return a != null && a.equals(b);
    }

    private static String nullAsEmpty(String s) {
        return s == null ? "" : s;
    }

    /** {@code rtrim(s)} in ASE: blanks only; an empty result is NULL. */
    private static String aseRtrim(String s) {
        if (s == null) {
            return null;
        }
        String r = stripBlanks(s);
        return r.isEmpty() ? null : r;
    }

    /** {@code ltrim(rtrim(s))} in ASE: blanks only; an empty result is NULL. */
    private static String aseTrim(String s) {
        String r = aseRtrim(s);
        if (r == null) {
            return null;
        }
        int start = 0;
        while (start < r.length() && r.charAt(start) == ' ') {
            start++;
        }
        return r.substring(start);
    }

    /** A T-SQL LIKE pattern ({@code %} any run, {@code _} one character) as a regex; everything else literal. */
    private static Pattern like(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '%') {
                regex.append(".*");
            } else if (ch == '_') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
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

    // ---- OrderHeaderRepository (bp_total_orden 1886-1898, bp_total_orden_his 1908-1920) ----------

    @Override
    public int markLiveInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
        return headerUpdate(HeaderTable.LIVE, ordenBanco, paymentForms, servicio, codError);
    }

    @Override
    public int markHistoryInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
        return headerUpdate(HeaderTable.HISTORY, ordenBanco, paymentForms, servicio, codError);
    }

    private int headerUpdate(HeaderTable table, Integer ordenBanco, List<String> paymentForms, String servicio,
                             int codError) {
        List<String> forms = Collections.unmodifiableList(new ArrayList<>(paymentForms));   // may hold a NULL
        if (headerUpdateThrows.contains(table)) {
            headerUpdates.add(new HeaderUpdate(table, ordenBanco, forms, servicio, codError, -1));
            throw new AsePortException("simulated @@error on update " + table);
        }
        if (!headersScripted) {
            // Phase 1/2 fixtures: the header exists in state I for whatever the step asks.
            int rows = table == HeaderTable.LIVE ? 1 : 0;
            headerUpdates.add(new HeaderUpdate(table, ordenBanco, forms, servicio, codError, rows));
            return rows;
        }
        List<HeaderRow> rows = headerTables.get(table);
        int count = 0;
        for (int i = 0; i < rows.size(); i++) {
            HeaderRow row = rows.get(i);
            boolean formMatches = forms.stream().anyMatch(f -> aseEquals(row.form(), f));
            boolean stateInitial = row.state() == null || aseEquals(row.state(), "I");
            if (ordenBanco != null && Objects.equals(row.order(), ordenBanco) && formMatches
                    && aseEquals(row.service(), servicio) && stateInitial) {
                rows.set(i, new HeaderRow(row.order(), row.form(), row.service(), "T", codError));
                count++;
            }
        }
        headerUpdates.add(new HeaderUpdate(table, ordenBanco, forms, servicio, codError, count));
        return count;
    }

    /** Sybase {@code =} on char/varchar: trailing blanks ignored, NULL never equal. */
    private static boolean aseEquals(String a, String b) {
        return a != null && b != null && stripBlanks(a).equals(stripBlanks(b));
    }

    private static String stripBlanks(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    private Map<HeaderTable, List<HeaderRow>> copyHeaders() {
        Map<HeaderTable, List<HeaderRow>> copy = new HashMap<>();
        headerTables.forEach((t, rows) -> copy.put(t, new ArrayList<>(rows)));
        return copy;
    }

    private void restoreHeaders(Map<HeaderTable, List<HeaderRow>> snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.forEach((t, rows) -> {
            headerTables.get(t).clear();
            headerTables.get(t).addAll(rows);
        });
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

    /** Every header UPDATE issued, in order (both tables). */
    public List<HeaderUpdate> headerUpdates() {
        return List.copyOf(headerUpdates);
    }

    /** The payment forms of the only header UPDATE issued; fails if there was not exactly one. */
    public List<String> onlyHeaderUpdateForms() {
        return only(headerUpdates, "header update").paymentForms();
    }

    /** The current rows of one header table (the committed state once the service returned). */
    public List<HeaderRow> headerRows(HeaderTable table) {
        return List.copyOf(headerTables.get(table));
    }

    /** {@code te_estado_proceso} of the only live row with that exact key; fails if there is not exactly one. */
    public String headerState(int order, String form, String service) {
        return onlyRow(HeaderTable.LIVE, order, form, service).state();
    }

    public String historyHeaderState(int order, String form, String service) {
        return onlyRow(HeaderTable.HISTORY, order, form, service).state();
    }

    public Integer headerCodError(int order, String form, String service) {
        return onlyRow(HeaderTable.LIVE, order, form, service).codError();
    }

    public Integer historyHeaderCodError(int order, String form, String service) {
        return onlyRow(HeaderTable.HISTORY, order, form, service).codError();
    }

    private HeaderRow onlyRow(HeaderTable table, int order, String form, String service) {
        List<HeaderRow> found = headerTables.get(table).stream()
                .filter(r -> Objects.equals(r.order(), order) && Objects.equals(r.form(), form)
                        && Objects.equals(r.service(), service))
                .toList();
        return only(found, table + " header row " + order + "/" + form + "/" + service);
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

    // ---- Phase 4 observations ------------------------------------------------------------------

    /** Every B7 read and notifier call, in order ({@code "ad_servicios_sms"}, {@code "bp_detalle:swift"}, ..., {@code "sp_eventos"}). */
    public List<String> notificationLog() {
        return List.copyOf(notificationLog);
    }

    public List<SmsQuery> smsQueries() {
        return List.copyOf(smsQueries);
    }

    public SmsQuery onlySmsQuery() {
        return only(smsQueries, "ad_servicios_sms read");
    }

    /** The {@code @i_servicio} values the {@code ad_notificacion_basica} read received (may hold a NULL). */
    public List<String> classQueries() {
        return Collections.unmodifiableList(new ArrayList<>(classQueries));
    }

    public List<BlockQuery> blockQueries() {
        return List.copyOf(blockQueries);
    }

    public BlockQuery onlyBlockQuery() {
        return only(blockQueries, "ba_bloqueaNotificacionSAT read");
    }

    public List<SwiftQuery> swiftQueries() {
        return List.copyOf(swiftQueries);
    }

    public List<OrderQuery> interbankQueries() {
        return List.copyOf(interbankQueries);
    }

    public List<OrderQuery> beneficiaryQueries() {
        return List.copyOf(beneficiaryQueries);
    }

    /** {@code "cc_ctacte:<account>"}, {@code "ah_cuenta:<account>"}, {@code "vi_cuenta:<account>"}. */
    public List<String> ownerQueries() {
        return List.copyOf(ownerQueries);
    }

    public List<BasicNotificationCommand> basicNotifications() {
        return List.copyOf(basicNotifications);
    }

    public BasicNotificationCommand onlyBasicNotification() {
        return only(basicNotifications, "pa_sat_pnotificacion");
    }

    public List<EventCommand> events() {
        return List.copyOf(events);
    }

    public EventCommand onlyEvent() {
        return only(events, "cob_internet..sp_eventos");
    }

    private static <T> T only(List<T> received, String what) {
        if (received.size() != 1) {
            throw new AssertionError("expected exactly one call to " + what + " but recorded " + received.size()
                    + ": " + received);
        }
        return received.get(0);
    }
}
