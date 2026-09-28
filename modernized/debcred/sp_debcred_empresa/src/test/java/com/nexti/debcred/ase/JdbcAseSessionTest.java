package com.nexti.debcred.ase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.nexti.debcred.AsePortException;
import com.nexti.debcred.CommissionCommand;
import com.nexti.debcred.CommissionResult;

/**
 * The JDBC adapter without an ASE (architecture reviews Phase 1 M6 and Phase 2 H2): the exact call text,
 * placeholder count, output-parameter index and binding of {@code sp_grb_comision} for both call shapes,
 * the literal transaction statements in autocommit mode, rollback-on-close, and the startup signature
 * check. Pins the adapter against the {@code CommissionCommand} record so a change to either cannot
 * silently shift {@code @o_error} by one slot.
 */
class JdbcAseSessionTest {

    private Connection connection;
    private CallableStatement call;
    private Statement statement;

    @BeforeEach
    void mocks() throws SQLException {
        connection = mock(Connection.class);
        call = mock(CallableStatement.class);
        statement = mock(Statement.class);
        when(connection.prepareCall(anyString())).thenReturn(call);
        when(connection.createStatement()).thenReturn(statement);
        when(call.getMoreResults()).thenReturn(false);
        when(call.getUpdateCount()).thenReturn(-1);
    }

    private static CommissionCommand command(String tipoafec) {
        LocalDateTime date = LocalDateTime.of(2026, 9, 27, 10, 0);
        return new CommissionCommand(0, "srv", "usrtest", "TERM01", 1, "N", "sp_test", date, "DIR", 500, 1, "TRANSWIFT",
                "L", 123456, new BigDecimal("2.00"), "COD:ABC", null, "CUE", 1, 3, "0000012345",
                tipoafec == null ? "COBRO DE COMISION" : "REF", "PROV", "P", 1, 34, "EMPTEST", 1, null, null, 4587, "1",
                tipoafec, "sp_debito_empresa", 0, new BigDecimal("0.10"),
                tipoafec == null ? new BigDecimal("0.20") : null, tipoafec == null ? new BigDecimal("0.30") : null,
                tipoafec == null ? new BigDecimal("0.40") : null, tipoafec == null ? new BigDecimal("0.50") : null,
                tipoafec == null ? new BigDecimal("0.60") : null);
    }

    private static String placeholders(int n) {
        return "?, ".repeat(n - 1) + "?";
    }

    @Test
    void theSessionStaysInAutocommitMode() throws SQLException {
        new JdbcAseSession(connection, "cobis");

        verify(connection).setAutoCommit(true);
        verify(connection, never()).setAutoCommit(false);
    }

    @Test
    void rule013_firstCommissionCallText42ParametersAndOErrorAt36() throws SQLException {
        when(call.getInt(1)).thenReturn(0);
        when(call.getObject(36)).thenReturn(122010);

        CommissionResult result = new JdbcAseSession(connection, "cobis").charge(command(null));

        verify(connection).prepareCall("{? = call cobis..sp_grb_comision(" + placeholders(41) + ")}");
        verify(call).registerOutParameter(1, Types.INTEGER);
        verify(call).registerOutParameter(36, Types.INTEGER);
        verify(call).setString(34, "sp_debito_empresa");        // @i_savepoint
        verify(call).setInt(35, 0);                              // @i_secuencial
        verify(call).setBigDecimal(37, new BigDecimal("0.10"));  // @i_valor_tarifa
        verify(call).setBigDecimal(42, new BigDecimal("0.60"));  // @i_valor_comision_che
        verify(call).setString(23, "COBRO DE COMISION");         // @i_referencia
        verify(call, never()).setString(eq(34), eq("16"));
        assertThat(result).isEqualTo(new CommissionResult(0, 122010));
    }

    @Test
    void rule016_swiftCommissionCallText38ParametersTipoafecAt34AndOErrorAt37() throws SQLException {
        when(call.getInt(1)).thenReturn(1);
        when(call.getObject(37)).thenReturn(0);

        CommissionResult result = new JdbcAseSession(connection, "cobis").charge(command("16"));

        verify(connection).prepareCall("{? = call cobis..sp_grb_comision(" + placeholders(37) + ")}");
        verify(call).setString(34, "16");                        // @i_tipoafec
        verify(call).setString(35, "sp_debito_empresa");         // @i_savepoint
        verify(call).setInt(36, 0);                              // @i_secuencial
        verify(call).registerOutParameter(37, Types.INTEGER);    // @o_error
        verify(call).setBigDecimal(38, new BigDecimal("0.10"));  // @i_valor_tarifa, the last one
        verify(call, never()).setBigDecimal(eq(39), any());
        assertThat(result).isEqualTo(new CommissionResult(1, 0));
    }

    @Test
    void resultSetsAreDrainedBeforeOutputsAreRead() throws SQLException {
        ResultSet rows = mock(ResultSet.class);
        when(call.getResultSet()).thenReturn(rows, (ResultSet) null);
        when(call.getMoreResults()).thenReturn(true, false);
        when(call.getUpdateCount()).thenReturn(-1);

        new JdbcAseSession(connection, "cobis").charge(command(null));

        InOrder order = inOrder(call);
        order.verify(call).execute();
        order.verify(call, times(2)).getMoreResults();
        order.verify(call).getInt(1);
    }

    @Test
    void aJdbcFailureIsAnAsePortException() throws SQLException {
        when(call.execute()).thenThrow(new SQLException("deadlock", "40001", 1205));

        assertThatThrownBy(() -> new JdbcAseSession(connection, "cobis").charge(command(null)))
                .isInstanceOf(AsePortException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void rule012_transactionStatementsAreIssuedLiterally() throws SQLException {
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");

        session.begin();
        session.savepoint("sp_debito_empresa");
        session.rollbackToSavepoint("sp_debito_empresa");
        session.rollback();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, times(4)).execute(sql.capture());
        assertThat(sql.getAllValues()).containsExactly(
                "begin tran", "save tran sp_debito_empresa", "rollback tran sp_debito_empresa", "rollback tran");
    }

    @Test
    void closingWithATransactionOpenRollsItBackAndNeverThrows() throws SQLException {
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");
        session.begin();
        org.mockito.Mockito.doThrow(new SQLException("gone")).when(connection).close();

        session.close();

        verify(statement).execute("rollback tran");
        verify(connection).close();
    }

    @Test
    void rule013_exitBLeavesNothingToRollBackOnClose() throws SQLException {
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");
        session.begin();
        session.rollback();                     // exit B's full rollback

        session.close();

        verify(statement, times(1)).execute("rollback tran");
    }

    @Test
    void anInvalidSavepointNameIsRefusedBeforeReachingTheServer() throws SQLException {
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");

        assertThatThrownBy(() -> session.savepoint("x; drop table t")).isInstanceOf(AsePortException.class);
        verify(statement, never()).execute(anyString());
    }

    @Test
    void theStartupCheckAcceptsTheAssumedOrderAndRefusesAnyOther() throws SQLException {
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        List<String> declared = ProcedureSignatureCheck.SP_GRB_COMISION;
        when(meta.getProcedureColumns("cobis", null, "sp_grb_comision", null)).thenAnswer(inv -> columns(declared));

        ProcedureSignatureCheck.verify(meta, "cobis", "sp_grb_comision", declared);

        List<String> swapped = new java.util.ArrayList<>(declared);
        java.util.Collections.swap(swapped, 33, 34);   // @i_savepoint <-> @i_secuencial
        when(meta.getProcedureColumns("cobis", null, "sp_grb_comision", null)).thenAnswer(inv -> columns(swapped));
        assertThatThrownBy(() -> ProcedureSignatureCheck.verify(meta, "cobis", "sp_grb_comision", declared))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fix the positional call");
    }

    @Test
    void rule014_rule018_headerUpdatesBindTheInListAndTargetLiveThenHistoryTables() throws SQLException {
        java.sql.PreparedStatement update = mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(update);
        when(update.executeUpdate()).thenReturn(2, 0);
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");

        int live = session.markLiveInTransition(123456, List.of("CUE", "EFE", "CHL"), "ROLPAGO", 0);
        int history = session.markHistoryInTransition(123456, java.util.Collections.singletonList(null), "ROLPAGO", 0);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        assertThat(sql.getAllValues().get(0)).isEqualTo("update cobis..bp_total_orden set te_estado_proceso = 'T', "
                + "te_codigo_error = ? where te_orden_banco = ? and te_frm_pagcob in (?, ?, ?) and te_servicio = ? "
                + "and isnull(te_estado_proceso, 'I') = 'I'");
        assertThat(sql.getAllValues().get(1)).startsWith("update db_sat_his..bp_total_orden_his set")
                .contains("te_frm_pagcob in (?) and");
        verify(update, times(2)).setInt(1, 0);
        verify(update).setString(3, "CUE");
        verify(update).setString(5, "CHL");
        verify(update).setString(6, "ROLPAGO");
        verify(update).setString(3, null);
        assertThat(live).isEqualTo(2);
        assertThat(history).isZero();
    }

    @Test
    void rule014_aFailedHeaderUpdateIsAnAsePortException() throws SQLException {
        java.sql.PreparedStatement update = mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(update);
        when(update.executeUpdate()).thenThrow(new SQLException("lock timeout", "HYT00", 12205));

        assertThatThrownBy(() -> new JdbcAseSession(connection, "cobis")
                .markLiveInTransition(1, List.of("COB"), "PAGOPRV", 0)).isInstanceOf(AsePortException.class);
    }

    @Test
    void h1_aDeadlockInsideTheTransactionIsATransactionLossAndCommitRefusesToReportSuccess() throws SQLException {
        java.sql.PreparedStatement update = mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(update);
        when(update.executeUpdate()).thenThrow(new SQLException("deadlock", "40001", 1205));
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");
        session.begin();

        assertThatThrownBy(() -> session.markLiveInTransition(1, List.of("CUE"), "ROLPAGO", 0))
                .isInstanceOf(com.nexti.debcred.AseTransactionAbortedException.class);
        assertThatThrownBy(session::commit).isInstanceOf(com.nexti.debcred.AseTransactionAbortedException.class);
        session.rollback();                                   // no second "rollback tran" against @@trancount 0
        session.close();
        verify(statement, never()).execute("rollback tran");
        verify(statement, never()).execute("commit tran");
    }

    @Test
    void h1_commitWithTrancountZeroIsRefused() throws SQLException {
        ResultSet count = mock(ResultSet.class);
        when(statement.executeQuery("select @@trancount")).thenReturn(count);
        when(count.next()).thenReturn(true);
        when(count.getInt(1)).thenReturn(0);
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");
        session.begin();

        assertThatThrownBy(session::commit).isInstanceOf(com.nexti.debcred.AseTransactionAbortedException.class);
        verify(statement, never()).execute("commit tran");
    }

    @Test
    void h1_commitWithAnOpenTransactionCommits() throws SQLException {
        ResultSet count = mock(ResultSet.class);
        when(statement.executeQuery("select @@trancount")).thenReturn(count);
        when(count.next()).thenReturn(true);
        when(count.getInt(1)).thenReturn(1);
        JdbcAseSession session = new JdbcAseSession(connection, "cobis");
        session.begin();

        session.commit();

        verify(statement).execute("commit tran");
    }

    @Test
    void m2_anEmptyPaymentFormListMatchesNothingWithoutSql() throws SQLException {
        assertThat(new JdbcAseSession(connection, "cobis").markLiveInTransition(1, List.of(), "ROLPAGO", 0)).isZero();
        verify(connection, never()).prepareStatement(anyString());
    }

    /** A metadata result set that yields one IN column per name, in order. */
    private static ResultSet columns(List<String> names) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        int[] row = {-1};
        when(rs.next()).thenAnswer(inv -> ++row[0] < names.size());
        when(rs.getShort("COLUMN_TYPE")).thenReturn((short) DatabaseMetaData.procedureColumnIn);
        when(rs.getString("COLUMN_NAME")).thenAnswer(inv -> names.get(row[0]));
        return rs;
    }
}
