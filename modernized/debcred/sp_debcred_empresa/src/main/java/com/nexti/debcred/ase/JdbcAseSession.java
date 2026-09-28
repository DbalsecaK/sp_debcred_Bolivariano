package com.nexti.debcred.ase;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexti.debcred.AccountingConfiguration;
import com.nexti.debcred.AccountingConfigurationQuery;
import com.nexti.debcred.AsePortException;
import com.nexti.debcred.AseSession;
import com.nexti.debcred.AseTransactionAbortedException;
import com.nexti.debcred.CommissionCommand;
import com.nexti.debcred.CommissionResult;
import com.nexti.debcred.CommissionTariffQuery;
import com.nexti.debcred.CommissionTariffResult;
import com.nexti.debcred.DebitNoteCommand;
import com.nexti.debcred.DebitNoteResult;
import com.nexti.debcred.ErrorReport;
import com.nexti.debcred.LedgerDebitCommand;
import com.nexti.debcred.LedgerDebitResult;
import com.nexti.debcred.MovementCommand;
import com.nexti.debcred.MovementResult;
import com.nexti.debcred.VirtualDebitNoteCommand;
import com.nexti.debcred.VirtualDebitNoteResult;

/**
 * {@link AseSession} over one JDBC connection to Sybase ASE.
 *
 * <p><b>Transaction mode (architecture review H1).</b> The connection stays in <b>autocommit /
 * unchained</b> mode and the transaction statements are issued literally ({@code begin tran},
 * {@code save tran name}, {@code rollback tran name}, {@code rollback tran}, {@code commit tran}),
 * exactly as the procedure did. With autocommit off, jTDS would put the session in chained mode, the
 * literal {@code begin tran} would nest and {@code commit tran} would never commit. Unchained mode
 * also allows Phase 2's exit B (a write after a full rollback that autocommits, lines 1622-1628).
 * <b>This is the first thing the bank's test ASE must confirm</b> (PREFLIGHT 3b, brief section 7 A4).
 *
 * <p>Procedures are called with {@code {? = call db..proc(?, ...)}} in the legacy argument order of
 * DATA_OBJECTS.md (assumed contracts, brief section 7 A5); every result set a procedure emits is
 * drained before the return value and output parameters are read. A JDBC failure becomes
 * {@link AsePortException}, the equivalent of {@code @@error <> 0}.
 *
 * <p>Closing the session with a transaction still open rolls it back and logs it; {@code close()}
 * never throws, so it cannot mask the failure that ended the debit.
 */
public final class JdbcAseSession implements AseSession {

    private static final Logger log = LoggerFactory.getLogger(JdbcAseSession.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,29}");

    private final Connection connection;
    private final String cobis;
    private boolean inTransaction;
    private boolean transactionLost;
    private String lastStatement = "(none)";

    JdbcAseSession(Connection connection, String cobisDatabase) throws SQLException {
        this.connection = connection;
        this.connection.setAutoCommit(true);
        this.cobis = identifier(cobisDatabase);
    }

    // ---- AseTransaction --------------------------------------------------------------------------

    @Override
    public void begin() {
        execute("begin tran");
        inTransaction = true;
    }

    @Override
    public void savepoint(String name) {
        execute("save tran " + identifier(name));
    }

    @Override
    public void rollbackToSavepoint(String name) {
        execute("rollback tran " + identifier(name));
    }

    @Override
    public void rollback() {
        if (transactionLost) {
            inTransaction = false;             // ASE already rolled it back; a second rollback would fail (3903)
            return;
        }
        execute("rollback tran");
        inTransaction = false;
    }

    /**
     * {@code commit tran}, refusing to report success when ASE already ended the transaction
     * ({@code @@trancount = 0}): a commit then commits nothing (review Phase 3 H1).
     */
    @Override
    public void commit() {
        if (transactionLost || tranCount() == 0) {
            inTransaction = false;
            throw new AseTransactionAbortedException("commit tran with @@trancount = 0: the debit was not persisted", null);
        }
        execute("commit tran");
        inTransaction = false;
    }

    private int tranCount() {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("select @@trancount")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw failure("select @@trancount", e);
        }
    }

    /**
     * A JDBC failure as {@link AsePortException}, or as {@link AseTransactionAbortedException} when ASE ended
     * the transaction: deadlock victim (1205) or a connection-class SQLState (08xxx).
     */
    private AsePortException failure(String what, SQLException e) {
        boolean lost = e.getErrorCode() == 1205 || (e.getSQLState() != null && e.getSQLState().startsWith("08"));
        if (lost && inTransaction) {
            transactionLost = true;
            return new AseTransactionAbortedException(what + " failed and ASE ended the transaction", e);
        }
        return new AsePortException(what + " failed", e);
    }

    @Override
    public void close() {
        try {
            if (inTransaction && !transactionLost) {
                log.warn("ASE session closed with a transaction open after {}: rolling it back", lastStatement);
                try (Statement s = connection.createStatement()) {
                    s.execute("rollback tran");
                }
            }
        } catch (SQLException e) {
            log.error("rollback on close failed (SQLState {}, code {})", e.getSQLState(), e.getErrorCode());
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("closing the ASE connection failed (SQLState {}, code {})", e.getSQLState(), e.getErrorCode());
            }
        }
    }

    // ---- procedures ------------------------------------------------------------------------------

    @Override
    public CommissionTariffResult consult(CommissionTariffQuery q) {
        return call("db_biz_admempresa..sp_con_comision", 10, cs -> {
            cs.setObject(2, q.codEmpresa(), Types.INTEGER);
            cs.setObject(3, q.codProducto(), Types.INTEGER);
            cs.setString(4, q.codServicio());
            cs.setString(5, q.codCanal());
            cs.setString(6, q.codAlcance());
            cs.setString(7, q.tipComision());
            cs.setInt(8, q.secuencia());
            cs.setString(9, q.aplcobis());
            cs.registerOutParameter(10, Types.DECIMAL);       // @o_valor_por_tran
        }, cs -> new CommissionTariffResult(cs.getInt(1), cs.getBigDecimal(10)));
    }

    @Override
    public AccountingConfiguration resolve(AccountingConfigurationQuery q) {
        boolean withConcept = q.concepto() != null;
        int outTrn = withConcept ? 12 : 11;
        int outCau = outTrn + 1;
        return call("db_biz_admempresa..sp_con_confcontable", outCau, cs -> {
            cs.setObject(2, q.producto(), Types.INTEGER);
            cs.setString(3, q.servicio());
            cs.setString(4, q.tipoafec());
            cs.setString(5, q.frmPagcob());
            cs.setString(6, q.canal());
            cs.setObject(7, q.tipcta(), Types.SMALLINT);
            cs.setString(8, q.moneda());
            cs.setString(9, q.referencia());
            cs.setObject(10, q.empresa(), Types.INTEGER);
            if (withConcept) {
                cs.setString(11, q.concepto());
            }
            cs.registerOutParameter(outTrn, Types.INTEGER);   // @o_trn
            cs.registerOutParameter(outCau, Types.VARCHAR);   // @o_cau
        }, cs -> new AccountingConfiguration(cs.getInt(1), (Integer) cs.getObject(outTrn), cs.getString(outCau)));
    }

    @Override
    public DebitNoteResult debit(DebitNoteCommand c) {
        return call(cobis + "..sp_ndc_ahcc", 28, cs -> {
            cs.setObject(2, c.sSsn(), Types.INTEGER);
            cs.setString(3, c.sSrv());
            cs.setString(4, c.sUser());
            cs.setString(5, c.sTerm());
            cs.setObject(6, c.sOfi(), Types.SMALLINT);
            cs.setTimestamp(7, timestamp(c.sDate()));
            cs.setObject(8, c.tTrn(), Types.INTEGER);
            cs.setString(9, c.iCuenta());
            cs.setObject(10, c.iTipoCuenta(), Types.SMALLINT);
            cs.setString(11, c.iCausal());
            cs.setBigDecimal(12, c.iValor());
            cs.setObject(13, c.iMon(), Types.SMALLINT);
            cs.setString(14, c.iTarjeta());
            cs.setString(15, c.iServicio());
            cs.setString(16, c.iRef());
            cs.setString(17, c.iAplcobis());
            cs.setString(18, c.iDetalle());
            cs.setBigDecimal(19, c.iTcomision());
            cs.setString(20, c.iCanal());
            cs.setString(21, c.iFrmPagcob());
            cs.setObject(22, c.iAlt(), Types.INTEGER);
            cs.setString(23, c.iAlternoDos());
            cs.setObject(24, c.iOrdenBanco(), Types.INTEGER);
            cs.setInt(25, c.iSecuencial());
            cs.setInt(26, c.iNchq());
            cs.setBigDecimal(27, c.iSolca());
            cs.registerOutParameter(28, Types.INTEGER);       // @o_transaccion
        }, cs -> new DebitNoteResult(cs.getInt(1), (Integer) cs.getObject(28)));
    }

    @Override
    public VirtualDebitNoteResult debit(VirtualDebitNoteCommand c) {
        return call("cob_virtuales..sp_vi_ndc_automatica", 18, cs -> {
            cs.setString(2, c.sSrv());
            cs.setObject(3, c.sOfi(), Types.SMALLINT);
            cs.setString(4, c.sUser());
            cs.setString(5, c.sTerm());
            cs.setObject(6, c.tTrn(), Types.INTEGER);
            cs.setString(7, c.iCta());
            cs.setBigDecimal(8, c.iVal());
            cs.setString(9, c.iCau());
            cs.setObject(10, c.iMon(), Types.SMALLINT);
            cs.setObject(11, c.iEmpresa(), Types.INTEGER);
            cs.setString(12, c.iCanal());
            cs.setString(13, c.iVerfEstadoCta());
            cs.setObject(14, c.iBatch() == null ? null : (c.iBatch() ? 1 : 0), Types.TINYINT);
            cs.setString(15, c.iRef());
            cs.setObject(16, c.iAlt(), Types.INTEGER);
            cs.registerOutParameter(17, Types.INTEGER);       // @o_error
            cs.registerOutParameter(18, Types.INTEGER);       // @o_ssn_monet
        }, cs -> new VirtualDebitNoteResult(cs.getInt(1), (Integer) cs.getObject(17), (Integer) cs.getObject(18)));
    }

    @Override
    public LedgerDebitResult debit(LedgerDebitCommand c) {
        return call("db_biz_pagos..sp_graba_tran_servicio", 20, cs -> {
            cs.setString(2, c.sSrv());
            cs.setObject(3, c.sOfi(), Types.SMALLINT);
            cs.setObject(4, c.sSsn(), Types.INTEGER);
            cs.setString(5, c.sUser());
            cs.setString(6, c.sTerm());
            cs.setObject(7, c.tTrn(), Types.INTEGER);
            cs.setTimestamp(8, timestamp(c.iFecha()));
            cs.setString(9, c.iReferencia());
            cs.setString(10, c.iCtaBanco());
            cs.setObject(11, c.iOficina(), Types.SMALLINT);
            cs.setInt(12, c.iIndicador());
            cs.setObject(13, c.iMoneda(), Types.SMALLINT);
            cs.setString(14, c.iCausa());
            cs.setBigDecimal(15, c.iSaldo());
            cs.setBigDecimal(16, c.iValor());
            cs.setInt(17, c.iOficinaCta());
            cs.setString(18, c.iTipoChequera());
            cs.setObject(19, c.iOrdenBanco(), Types.INTEGER);
            cs.setInt(20, c.iSecuencial());
        }, cs -> new LedgerDebitResult(cs.getInt(1)));
    }

    @Override
    public MovementResult record(MovementCommand c) {
        return call(cobis + "..sp_grb_mov_y_frmpgo", 34, cs -> {
            cs.setString(2, c.sUser());
            cs.setString(3, c.sTerm());
            cs.setObject(4, c.sOfi(), Types.SMALLINT);
            cs.setObject(5, c.tTrn(), Types.INTEGER);
            cs.setString(6, c.iTipoProceso());
            cs.setObject(7, c.iEmpresa(), Types.INTEGER);
            cs.setObject(8, c.iProducto(), Types.SMALLINT);
            cs.setObject(9, c.iOrdenBanco(), Types.INTEGER);
            cs.setString(10, c.iCanal());
            cs.setString(11, c.iCau());
            cs.setString(12, c.iEstProceso());
            cs.setObject(13, c.iCodError(), Types.INTEGER);
            cs.setString(14, c.iFrmPagcob());
            cs.setObject(15, c.iMonedaOrden(), Types.SMALLINT);
            cs.setBigDecimal(16, c.iValorMov());
            cs.setString(17, c.iTipoAfectacion());
            cs.setTimestamp(18, timestamp(c.iFchContab()));
            cs.setString(19, c.iReferencia());
            cs.setInt(20, c.iSecuencial());
            cs.setString(21, c.iServicio());
            cs.setString(22, c.iTipoPagcob());
            cs.setObject(23, c.iPaisCta(), Types.SMALLINT);
            cs.setObject(24, c.iCodBancoCta(), Types.SMALLINT);
            cs.setObject(25, c.iTipoCta(), Types.SMALLINT);
            cs.setString(26, c.iNumeroCta());
            cs.setBigDecimal(27, c.iValorOrdenado());
            cs.setString(28, c.iNemOrdenante());
            cs.setObject(29, c.iLocalidadPagcob(), Types.INTEGER);
            cs.setString(30, c.iNombreCuenta());
            cs.setString(31, c.iNombreBeneficiario());
            cs.setObject(32, c.iOrdenEmpresa(), Types.INTEGER);
            cs.setBigDecimal(33, c.iValorComision());
            cs.setObject(34, c.iTranNcnd(), Types.INTEGER);
        }, cs -> new MovementResult(cs.getInt(1)));
    }

    @Override
    public CommissionResult charge(CommissionCommand c) {
        // sp_grb_comision, positional in the declaration order ProcedureSignatureCheck verifies at startup.
        // First call (1518-1594): 32 fixed, savepoint, secuencial, @o_error, the six REF33 fields -> last 42.
        // SWIFT call (1746-1820): 32 fixed, @i_tipoafec, savepoint, secuencial, @o_error, @i_valor_tarifa -> last 38;
        // the five other REF33 fields are omitted, as the legacy omits them (review Phase 2 H1).
        boolean swift = c.iTipoafec() != null;
        int last = swift ? 38 : 42;
        int oErrorIndex = swift ? 37 : 36;
        return call(cobis + "..sp_grb_comision", last, cs -> {
            int i = 2;
            cs.setObject(i++, c.sSsn(), Types.INTEGER);
            cs.setString(i++, c.sSrv());
            cs.setString(i++, c.sUser());
            cs.setString(i++, c.sTerm());
            cs.setObject(i++, c.sOfi(), Types.SMALLINT);
            cs.setString(i++, c.iAplcobis());
            cs.setString(i++, c.iSpName());
            cs.setTimestamp(i++, timestamp(c.iFechaProceso()));
            cs.setString(i++, c.iCanalComision());
            cs.setObject(i++, c.iEmpresa(), Types.INTEGER);
            cs.setObject(i++, c.iProducto(), Types.SMALLINT);
            cs.setString(i++, c.iServicio());
            cs.setString(i++, c.iTipoProceso());
            cs.setObject(i++, c.iOrdenBanco(), Types.INTEGER);
            cs.setBigDecimal(i++, c.iValorComision());
            cs.setString(i++, c.iCadena());
            cs.setString(i++, c.iTarjeta());
            cs.setString(i++, c.iFrmPagcob());
            cs.setObject(i++, c.iMoneda(), Types.SMALLINT);
            cs.setObject(i++, c.iTipctaEmp(), Types.SMALLINT);
            cs.setString(i++, c.iNumctaEmp());
            cs.setString(i++, c.iReferencia());
            cs.setString(i++, c.iDetalleRef());
            cs.setString(i++, c.iTipoPagcob());
            cs.setObject(i++, c.iPaisCta(), Types.SMALLINT);
            cs.setObject(i++, c.iCodBancoCta(), Types.SMALLINT);
            cs.setString(i++, c.iNemOrdenante());
            cs.setObject(i++, c.iLocalidadPagcob(), Types.INTEGER);
            cs.setString(i++, c.iNombreCuenta());
            cs.setString(i++, c.iNombreBeneficiario());
            cs.setObject(i++, c.iOrdenEmpresa(), Types.INTEGER);
            cs.setString(i++, c.iTipoHorario());
            if (swift) {
                cs.setString(i++, c.iTipoafec());
            }
            cs.setString(i++, c.iSavepoint());
            cs.setInt(i++, c.iSecuencial());
            cs.registerOutParameter(i++, Types.INTEGER);      // @o_error
            cs.setBigDecimal(i++, c.iValorTarifa());
            if (swift) {
                return;
            }
            cs.setBigDecimal(i++, c.iValorComisionCue());
            cs.setBigDecimal(i++, c.iValorTarifaEfe());
            cs.setBigDecimal(i++, c.iValorComisionEfe());
            cs.setBigDecimal(i++, c.iValorTarifaChe());
            cs.setBigDecimal(i, c.iValorComisionChe());
        }, cs -> new CommissionResult(cs.getInt(1), (Integer) cs.getObject(oErrorIndex)));
    }

    @Override
    public void report(ErrorReport r) {
        // exec cobis..sp_cerror @t_from = @i_sp_name, @i_num = @w_num_error (2152-2156)
        lastStatement = "cobis..sp_cerror";
        try (CallableStatement cs = connection.prepareCall("{call cobis..sp_cerror(?, ?)}")) {
            cs.setString(1, r.from());
            cs.setInt(2, r.num());
            cs.execute();
            drain(cs);
        } catch (SQLException e) {
            throw failure("cobis..sp_cerror", e);
        }
    }

    // ---- reads -----------------------------------------------------------------------------------

    @Override
    public Optional<String> ndcorpeiConcept() {
        // 484-496: ct_cod_catalogo of the active ad_concepto_contable row whose other field is NDCORPEI
        return queryString("""
                select c.ct_cod_catalogo
                  from db_biz_admempresa..ba_tabla t, db_biz_admempresa..ba_catalogo c
                 where t.tb_tabla = 'ad_concepto_contable'
                   and c.ct_tabla = t.tb_codigo
                   and c.ct_otro_campo_catalogo = 'NDCORPEI'
                   and c.ct_estado = 'A'""", "ba_catalogo:NDCORPEI", ps -> { });
    }

    @Override
    public boolean isBasicAccount(String ctaBanco) {
        // 300-304
        return queryString("select '1' from cob_virtuales..vi_cuenta where vi_cta_banco = ? and vi_prod_banc = 13",
                "vi_cuenta", ps -> ps.setString(1, ctaBanco)).isPresent();
    }

    @Override
    public Optional<String> liveService(Integer ordenBanco) {
        return queryString("select or_servicio from db_biz_pagos..bp_orden where or_orden_banco = ?",
                "bp_orden", ps -> ps.setObject(1, ordenBanco, Types.INTEGER));
    }

    @Override
    public Optional<String> historyService(Integer ordenBanco) {
        return queryString("select or_servicio from db_sat_his..bp_orden_his where or_orden_banco = ?",
                "bp_orden_his", ps -> ps.setObject(1, ordenBanco, Types.INTEGER));
    }

    // ---- order header (B12) ----------------------------------------------------------------------

    /**
     * The legacy names {@code bp_total_orden} unqualified: it resolves in the procedure's home database,
     * which brief section 7 A8 says is {@code cobis} ({@code debcred.ase.cobis-database}, the same home
     * database the unqualified procedures use). {@code cobis..sp_cerror} and {@code db_sat_his..} are
     * qualified literally in the legacy and stay literal here (review Phase 3 M1).
     */
    @Override
    public int markLiveInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
        return markInTransition(cobis + "..bp_total_orden", ordenBanco, paymentForms, servicio, codError);
    }

    @Override
    public int markHistoryInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
        return markInTransition("db_sat_his..bp_total_orden_his", ordenBanco, paymentForms, servicio, codError);
    }

    /**
     * 1886-1898 / 1908-1920. {@code table} is only ever a literal or the validated home-database identifier
     * plus a literal. The IN list is bound, one placeholder per form. A single NULL form matches nothing
     * <b>only if</b> {@code te_frm_pagcob} is NOT NULL or the session runs with {@code ansinull on}; with
     * {@code ansinull off}, Sybase reads {@code col in (NULL)} as {@code col is null}. To confirm on the
     * bank's ASE together with the DDL (review Phase 3 M3).
     */
    private int markInTransition(String table, Integer ordenBanco, List<String> forms, String servicio, int codError) {
        if (forms.isEmpty()) {
            return 0;                          // an empty IN list matches nothing (review Phase 3 M2)
        }
        lastStatement = "update " + table;
        String in = "?, ".repeat(forms.size() - 1) + "?";
        String sql = "update " + table + " set te_estado_proceso = 'T', te_codigo_error = ?"
                + " where te_orden_banco = ? and te_frm_pagcob in (" + in + ") and te_servicio = ?"
                + " and isnull(te_estado_proceso, 'I') = 'I'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, codError);
            ps.setObject(i++, ordenBanco, Types.INTEGER);
            for (String form : forms) {
                ps.setString(i++, form);
            }
            ps.setString(i, servicio);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("update " + table, e);
        }
    }

    // ---- plumbing --------------------------------------------------------------------------------

    private interface Binder<S extends Statement> {
        void bind(S statement) throws SQLException;
    }

    private interface Reader<T> {
        T read(CallableStatement statement) throws SQLException;
    }

    /**
     * {@code {? = call proc(?, ...)}} with {@code lastParameter} placeholders after the return value.
     * Result sets the procedure emits ({@code select}, {@code print}) are drained first: jTDS refuses to
     * read output parameters while one is pending.
     */
    private <T> T call(String procedure, int lastParameter, Binder<CallableStatement> bind, Reader<T> read) {
        lastStatement = procedure;
        String placeholders = "?, ".repeat(lastParameter - 1) + "?";
        try (CallableStatement cs = connection.prepareCall("{? = call " + procedure + "(" + placeholders + ")}")) {
            cs.registerOutParameter(1, Types.INTEGER);
            bind.bind(cs);
            cs.execute();
            drain(cs);
            return read.read(cs);
        } catch (SQLException e) {
            throw failure(procedure, e);
        }
    }

    private static void drain(CallableStatement cs) throws SQLException {
        do {
            try (ResultSet rs = cs.getResultSet()) {
                // discarded: the legacy caller never read these rows either
            }
        } while (cs.getMoreResults() || cs.getUpdateCount() != -1);
    }

    private Optional<String> queryString(String sql, String what, Binder<PreparedStatement> bind) {
        lastStatement = what;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure("query on " + what, e);
        }
    }

    private void execute(String statement) {
        lastStatement = statement;
        try (Statement s = connection.createStatement()) {
            s.execute(statement);
        } catch (SQLException e) {
            throw failure(statement, e);
        }
    }

    private static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new AsePortException("not a valid ASE identifier");
        }
        return name;
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
