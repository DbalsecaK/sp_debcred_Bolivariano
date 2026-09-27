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
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexti.debcred.AccountingConfiguration;
import com.nexti.debcred.AccountingConfigurationQuery;
import com.nexti.debcred.AsePortException;
import com.nexti.debcred.AseSession;
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
        execute("rollback tran");
        inTransaction = false;
    }

    @Override
    public void commit() {
        execute("commit tran");
        inTransaction = false;
    }

    @Override
    public void close() {
        try {
            if (inTransaction) {
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
    public void report(ErrorReport r) {
        // exec cobis..sp_cerror @t_from = @i_sp_name, @i_num = @w_num_error (2152-2156)
        lastStatement = "cobis..sp_cerror";
        try (CallableStatement cs = connection.prepareCall("{call cobis..sp_cerror(?, ?)}")) {
            cs.setString(1, r.from());
            cs.setInt(2, r.num());
            cs.execute();
            drain(cs);
        } catch (SQLException e) {
            throw new AsePortException("cobis..sp_cerror failed", e);
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
            throw new AsePortException(procedure + " failed", e);
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
            throw new AsePortException("query on " + what + " failed", e);
        }
    }

    private void execute(String statement) {
        lastStatement = statement;
        try (Statement s = connection.createStatement()) {
            s.execute(statement);
        } catch (SQLException e) {
            throw new AsePortException(statement + " failed", e);
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
