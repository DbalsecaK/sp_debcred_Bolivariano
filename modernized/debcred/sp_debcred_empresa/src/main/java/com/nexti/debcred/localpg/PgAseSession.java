package com.nexti.debcred.localpg;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AseSession} over the <b>local PostgreSQL test environment</b> (profile {@code local-pg},
 * {@code local-pg/debcred-local.sql}). Not production: production calls the real COBIS procedures in
 * Sybase ASE through {@code JdbcAseSession}. Here the ten procedures are PL/pgSQL simulations that take the
 * command record as one {@code jsonb} argument.
 *
 * <p>Transactions use the JDBC API. Sybase rolls back only the failing <b>statement</b>, while PostgreSQL
 * aborts the whole transaction on any error; every call inside a transaction therefore runs under its own
 * internal savepoint, so a statement failure behaves as in ASE (the legacy's unchecked {@code @@error}
 * parity keeps working). After a full rollback the connection goes back to autocommit, so exit B's
 * failure movement is committed on its own, as in the legacy.
 */
public final class PgAseSession implements AseSession {

    private static final Logger log = LoggerFactory.getLogger(PgAseSession.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Connection connection;
    private final Map<String, Savepoint> savepoints = new HashMap<>();
    private boolean inTransaction;

    PgAseSession(Connection connection) throws SQLException {
        this.connection = connection;
        this.connection.setAutoCommit(true);
    }

    // ---- transaction -----------------------------------------------------------------------------

    @Override
    public void begin() {
        try {
            connection.setAutoCommit(false);
            inTransaction = true;
        } catch (SQLException e) {
            throw failure("begin", e);
        }
    }

    @Override
    public void savepoint(String name) {
        try {
            savepoints.put(name, connection.setSavepoint(name));
        } catch (SQLException e) {
            throw failure("savepoint " + name, e);
        }
    }

    @Override
    public void rollbackToSavepoint(String name) {
        try {
            connection.rollback(savepoints.get(name));
        } catch (SQLException e) {
            throw failure("rollback to savepoint " + name, e);
        }
    }

    @Override
    public void rollback() {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw failure("rollback", e);
        } finally {
            inTransaction = false;
            savepoints.clear();
        }
    }

    @Override
    public void commit() {
        try {
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new AseTransactionAbortedException("commit failed: the debit was not persisted", e);
        } finally {
            inTransaction = false;
            savepoints.clear();
        }
    }

    @Override
    public void close() {
        try {
            if (inTransaction) {
                log.warn("local PostgreSQL session closed with a transaction open: rolling it back");
                connection.rollback();
            }
        } catch (SQLException e) {
            log.error("rollback on close failed: {}", e.getSQLState());
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("closing the connection failed: {}", e.getSQLState());
            }
        }
    }

    // ---- simulated procedures ----------------------------------------------------------------------

    @Override
    public CommissionTariffResult consult(CommissionTariffQuery q) {
        return call("db_biz_admempresa.sp_con_comision", q,
                rs -> new CommissionTariffResult(rs.getInt(1), rs.getBigDecimal(2)));
    }

    @Override
    public AccountingConfiguration resolve(AccountingConfigurationQuery q) {
        return call("db_biz_admempresa.sp_con_confcontable", q,
                rs -> new AccountingConfiguration(rs.getInt(1), (Integer) rs.getObject(2), rs.getString(3)));
    }

    @Override
    public DebitNoteResult debit(DebitNoteCommand c) {
        return call("cobis.sp_ndc_ahcc", c, rs -> new DebitNoteResult(rs.getInt(1), (Integer) rs.getObject(2)));
    }

    @Override
    public VirtualDebitNoteResult debit(VirtualDebitNoteCommand c) {
        return call("cob_virtuales.sp_vi_ndc_automatica", c,
                rs -> new VirtualDebitNoteResult(rs.getInt(1), (Integer) rs.getObject(2), (Integer) rs.getObject(3)));
    }

    @Override
    public LedgerDebitResult debit(LedgerDebitCommand c) {
        return call("db_biz_pagos.sp_graba_tran_servicio", c, rs -> new LedgerDebitResult(rs.getInt(1)));
    }

    @Override
    public MovementResult record(MovementCommand c) {
        return call("cobis.sp_grb_mov_y_frmpgo", c, rs -> new MovementResult(rs.getInt(1)));
    }

    @Override
    public CommissionResult charge(CommissionCommand c) {
        return call("cobis.sp_grb_comision", c, rs -> new CommissionResult(rs.getInt(1), (Integer) rs.getObject(2)));
    }

    @Override
    public void report(ErrorReport r) {
        call("cobis.sp_cerror", r, rs -> rs.getInt(1));
    }

    // ---- reads and the order header ----------------------------------------------------------------

    @Override
    public Optional<String> ndcorpeiConcept() {
        return queryString("select c.ct_cod_catalogo from db_biz_admempresa.ba_tabla t"
                + " join db_biz_admempresa.ba_catalogo c on c.ct_tabla = t.tb_codigo"
                + " where t.tb_tabla = 'ad_concepto_contable' and c.ct_otro_campo_catalogo = 'NDCORPEI'"
                + " and c.ct_estado = 'A'", ps -> { });
    }

    @Override
    public boolean isBasicAccount(String ctaBanco) {
        return queryString("select '1' from cob_virtuales.vi_cuenta where vi_cta_banco = ? and vi_prod_banc = 13",
                ps -> ps.setString(1, ctaBanco)).isPresent();
    }

    @Override
    public Optional<String> liveService(Integer ordenBanco) {
        return queryString("select or_servicio from db_biz_pagos.bp_orden where or_orden_banco = ?",
                ps -> ps.setObject(1, ordenBanco, Types.INTEGER));
    }

    @Override
    public Optional<String> historyService(Integer ordenBanco) {
        return queryString("select or_servicio from db_sat_his.bp_orden_his where or_orden_banco = ?",
                ps -> ps.setObject(1, ordenBanco, Types.INTEGER));
    }

    @Override
    public int markLiveInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
        return markInTransition("cobis.bp_total_orden", ordenBanco, paymentForms, servicio, codError);
    }

    @Override
    public int markHistoryInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError) {
        return markInTransition("db_sat_his.bp_total_orden_his", ordenBanco, paymentForms, servicio, codError);
    }

    /** Sybase char semantics: trailing blanks ignored ({@code rtrim}); a NULL form matches nothing. */
    private int markInTransition(String table, Integer ordenBanco, List<String> forms, String servicio, int codError) {
        if (forms.isEmpty()) {
            return 0;
        }
        String in = "?, ".repeat(forms.size() - 1) + "?";
        String sql = "update " + table + " set te_estado_proceso = 'T', te_codigo_error = ?"
                + " where te_orden_banco = ? and rtrim(te_frm_pagcob) in (" + in + ")"
                + " and rtrim(te_servicio) = rtrim(?) and coalesce(te_estado_proceso, 'I') = 'I'";
        return statement("update " + table, () -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                ps.setInt(i++, codError);
                ps.setObject(i++, ordenBanco, Types.INTEGER);
                for (String form : forms) {
                    ps.setString(i++, form == null ? null : form.stripTrailing());
                }
                ps.setString(i, servicio);
                return ps.executeUpdate();
            }
        });
    }

    // ---- plumbing ----------------------------------------------------------------------------------

    private interface Work<T> {
        T run() throws SQLException;
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private interface Reader<T> {
        T read(ResultSet row) throws SQLException;
    }

    private <T> T call(String function, Object command, Reader<T> read) {
        String json = JSON.writeValueAsString(command);
        return statement(function, () -> {
            try (PreparedStatement ps = connection.prepareStatement("select * from " + function + "(?::jsonb)")) {
                ps.setString(1, json);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException(function + " returned no row");
                    }
                    return read.read(rs);
                }
            }
        });
    }

    private Optional<String> queryString(String sql, Binder bind) {
        return statement("query", () -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
                }
            }
        });
    }

    /** One statement with Sybase statement-level atomicity: inside a transaction, its own savepoint. */
    private <T> T statement(String what, Work<T> work) {
        Savepoint statementSavepoint = null;
        try {
            if (inTransaction) {
                statementSavepoint = connection.setSavepoint();
            }
            T result = work.run();
            if (statementSavepoint != null) {
                connection.releaseSavepoint(statementSavepoint);
            }
            return result;
        } catch (SQLException e) {
            if (statementSavepoint != null) {
                try {
                    connection.rollback(statementSavepoint);
                } catch (SQLException ignored) {
                    // the transaction itself is gone; failure() reports it as lost
                }
            }
            throw failure(what, e);
        }
    }

    /** 40P01 (deadlock) and 08xxx (connection) end the transaction in PostgreSQL. */
    private AsePortException failure(String what, SQLException e) {
        String state = e.getSQLState();
        boolean lost = state != null && (state.equals("40P01") || state.startsWith("08"));
        if (lost && inTransaction) {
            return new AseTransactionAbortedException(what + " failed and the transaction ended", e);
        }
        return new AsePortException(what + " failed", e);
    }
}
