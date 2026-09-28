package com.nexti.debcred.ase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nexti.debcred.AsePortException;
import com.nexti.debcred.BasicNotificationCommand;
import com.nexti.debcred.BasicNotificationResult;
import com.nexti.debcred.BeneficiaryDetail;
import com.nexti.debcred.EventCommand;
import com.nexti.debcred.EventResult;
import com.nexti.debcred.InterbankCreditDetail;
import com.nexti.debcred.SwiftCreditDetail;
import com.nexti.debcred.VirtualAccountOwner;

/**
 * The Phase 4 JDBC calls without an ASE (as {@code JdbcAseSessionTest} does for Phases 1-3): the SQL of
 * each B7 read (tables, the legacy catalogue columns and filters, live vs {@code db_sat_his}, the home
 * database of the unqualified {@code bp_detalle} and {@code pa_sat_pnotificacion}, brief 7 A8), the bound
 * arguments, how rows become records, and the call text / parameter positions of the two notifiers.
 * Legacy lines: 798-810, 826-856, 892-932, 998-1010, 1024-1044, 1052-1082, 1110-1152, 1166-1180, 1210-1242.
 *
 * <p>Call text: {@code {? = call proc(...)}} carries exactly one {@code ?} per procedure parameter, so the
 * parameters are indices 2..(count + 1): 15 for {@code pa_sat_pnotificacion}, 14 for {@code sp_eventos}. (The
 * Phase 1-3 helper {@code call(procedure, lastParameter, ...)} emits {@code lastParameter} placeholders, one
 * more than it binds; that is reported separately and not reproduced here.)
 *
 * <p>SQL is compared after {@link #n normalization} (lower case, table aliases and all whitespace removed),
 * so the pins are the legacy predicates, not a formatting. The home database is {@code cobis_alt} here to
 * prove it is the configured one (A8), never a literal.
 */
class JdbcAseSessionNotificationPortsTest {

    private static final String ACCOUNT = "0000012345";

    private Connection connection;
    private CallableStatement call;
    private PreparedStatement query;

    @BeforeEach
    void mocks() throws SQLException {
        connection = mock(Connection.class);
        call = mock(CallableStatement.class);
        query = mock(PreparedStatement.class);
        when(connection.prepareCall(anyString())).thenReturn(call);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(call.getMoreResults()).thenReturn(false);
        when(call.getUpdateCount()).thenReturn(-1);
    }

    private JdbcAseSession session() throws SQLException {
        return new JdbcAseSession(connection, "cobis_alt");
    }

    /** Lower case, {@code alias.} prefixes removed ({@code db..table} kept), all whitespace removed. */
    static String n(String sql) {
        return sql.toLowerCase()
                .replaceAll("(?<![a-z0-9_.])[a-z_][a-z0-9_]*\\.(?!\\.)(?=[a-z_])", "")
                .replaceAll("\\s+", "");
    }

    private String onlySql() throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        return n(sql.getValue());
    }

    /** Successive executeQuery() answers; the result sets are built BEFORE the stubbing starts (Mockito rule). */
    private void queryReturns(ResultSet first, ResultSet... more) throws SQLException {
        when(query.executeQuery()).thenReturn(first, more);
    }

    private static void assertCatalogueJoin(String sql) {
        assertThat(sql.contains(n("tb_cod_tabla = ct_cod_tabla")) || sql.contains(n("ct_cod_tabla = tb_cod_tabla")))
                .as("ba_tabla joined to ba_catalogo on tb_cod_tabla = ct_cod_tabla in: %s", sql).isTrue();
    }

    /**
     * A result set over literal rows. Columns are answered by index (1-based) and by the given labels, through
     * getString / getObject / getInt / getShort, with wasNull() reflecting the last read.
     */
    private static ResultSet rows(List<String> labels, Object[]... data) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        int[] row = {-1};
        boolean[] lastNull = {false};
        when(rs.next()).thenAnswer(inv -> ++row[0] < data.length);
        when(rs.wasNull()).thenAnswer(inv -> lastNull[0]);
        org.mockito.stubbing.Answer<Object> byIndex = inv -> {
            Object v = data[row[0]][(Integer) inv.getArgument(0) - 1];
            lastNull[0] = v == null;
            return v;
        };
        org.mockito.stubbing.Answer<Object> byLabel = inv -> {
            int i = labels.indexOf(((String) inv.getArgument(0)).toLowerCase());
            if (i < 0) {
                throw new SQLException("no column " + inv.getArgument(0));
            }
            Object v = data[row[0]][i];
            lastNull[0] = v == null;
            return v;
        };
        when(rs.getObject(anyInt())).thenAnswer(byIndex);
        when(rs.getObject(anyString())).thenAnswer(byLabel);
        when(rs.getString(anyInt())).thenAnswer(inv -> (String) byIndex.answer(inv));
        when(rs.getString(anyString())).thenAnswer(inv -> (String) byLabel.answer(inv));
        when(rs.getInt(anyInt())).thenAnswer(inv -> {
            Object v = byIndex.answer(inv);
            return v == null ? 0 : ((Number) v).intValue();
        });
        when(rs.getInt(anyString())).thenAnswer(inv -> {
            Object v = byLabel.answer(inv);
            return v == null ? 0 : ((Number) v).intValue();
        });
        when(rs.getShort(anyInt())).thenAnswer(inv -> {
            Object v = byIndex.answer(inv);
            return v == null ? (short) 0 : ((Number) v).shortValue();
        });
        return rs;
    }

    // ---- catalogue ---------------------------------------------------------------------------------

    @Test
    void rule035_a16_smsServiceReadFiltersLikeTheLegacyAndOrdersByCode() throws SQLException {
        ResultSet rs = rows(List.of("ct_cod_catalogo"), new Object[] {"TRC-SAT"}, new Object[] {"TRX-SAT"});
        queryReturns(rs);

        List<String> codes = session().smsServiceCodes("TRANSCLI ", "SAT");

        String sql = onlySql();
        assertThat(sql).startsWith(n("select ct_cod_catalogo"))
                .contains(n("db_biz_admempresa..ba_tabla"), n("db_biz_admempresa..ba_catalogo"),
                        n("tb_nom_tabla = 'ad_servicios_sms'"),
                        n("ct_nom_catalogo like '%' + ltrim(rtrim(?)) + '%'"),
                        n("right(ltrim(rtrim(ct_cod_catalogo)), 3) = ?"),
                        n("ct_est_catalogo = 'A'"),
                        n("order by ct_cod_catalogo"))
                .as("798-810 has no tb_est_tabla filter").doesNotContain("tb_est_tabla");
        assertCatalogueJoin(sql);
        verify(query).setString(1, "TRANSCLI ");     // raw: the SQL trims it, % and _ stay wildcards (no escaping)
        verify(query).setString(2, "SAT");
        assertThat(codes).containsExactly("TRC-SAT", "TRX-SAT");
    }

    @Test
    void rule035_smsServiceReadWithNoRowIsAnEmptyList() throws SQLException {
        queryReturns(rows(List.of("ct_cod_catalogo")));

        assertThat(session().smsServiceCodes("ROLPAGO", "SAT")).isEmpty();
    }

    @Test
    void rule036_classificationReadUsesTheExactCodeAndBothActiveFlags() throws SQLException {
        queryReturns(rows(List.of("ct_otro_campo_catalogo"), new Object[] {"B"}));

        Optional<String> cls = session().notificationClass("ROLPAGO  ");

        String sql = onlySql();
        assertThat(sql).startsWith(n("select ct_otro_campo_catalogo"))
                .contains(n("tb_nom_tabla = 'ad_notificacion_basica'"), n("ct_cod_catalogo = ?"),
                        n("tb_est_tabla = 'A'"), n("ct_est_catalogo = 'A'"))
                .doesNotContain("ltrim", "like");
        assertCatalogueJoin(sql);
        verify(query).setString(1, "ROLPAGO  ");
        assertThat(cls).contains("B");
    }

    @Test
    void rule036_classificationNoRowOrNullValueIsEmpty() throws SQLException {
        queryReturns(rows(List.of("ct_otro_campo_catalogo")),
                rows(List.of("ct_otro_campo_catalogo"), new Object[] {null}));
        JdbcAseSession session = session();

        assertThat(session.notificationClass("ROLPAGO")).isEmpty();
        assertThat(session.notificationClass("ROLPAGO")).isEmpty();
    }

    @Test
    void rule021_blockListReadBuildsTheKeyInSqlAndIgnoresTheTableState() throws SQLException {
        queryReturns(rows(List.of("x"), new Object[] {"1"}), rows(List.of("x")));
        JdbcAseSession session = session();

        boolean blocked = session.notificationBlocked("ROLPAGO ", "ROL", "sp_x_fake ");
        boolean notBlocked = session.notificationBlocked("ROLPAGO", "RPG", "sp_y_fake");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, org.mockito.Mockito.times(2)).prepareStatement(sql.capture());
        String s = n(sql.getAllValues().get(0));
        assertThat(s).contains(n("tb_nom_tabla = 'ba_bloqueaNotificacionSAT'"),
                        n("isnull(ct_nom_catalogo, '') = rtrim(?) + '-' + rtrim(?)"),
                        n("isnull(ct_otro_campo_catalogo, '') = rtrim(?)"),
                        n("ct_est_catalogo = 'A'"))
                .as("1166-1180 has no tb_est_tabla filter").doesNotContain("tb_est_tabla");
        assertCatalogueJoin(s);
        verify(query).setString(1, "ROLPAGO ");
        verify(query).setString(2, "ROL");
        verify(query).setString(3, "sp_x_fake ");
        assertThat(blocked).isTrue();
        assertThat(notBlocked).isFalse();
    }

    // ---- order detail --------------------------------------------------------------------------------

    @Test
    void rule019_liveSwiftDetailJoinsOrderAndDetailByOrderSequenceAndOrderingCompany() throws SQLException {
        queryReturns(rows(List.of("dt_referencia_grupo", "dt_nom_cuenta"),
                new Object[] {"GRPFAKE01", "CUENTA UNO"}, new Object[] {"GRPFAKE02", null}));

        List<SwiftCreditDetail> found = session().liveSwiftCreditDetails(123456, 2, 500);

        String sql = onlySql();
        assertThat(sql).startsWith(n("select dt_referencia_grupo, dt_nom_cuenta"))
                .contains(n("db_biz_pagos..bp_orden"), n("db_biz_pagos..bp_detalle"), n("or_orden_banco = ?"),
                        n("dt_secuencial = ?"), n("or_ordenante = ?"))
                .doesNotContain("db_sat_his");
        assertThat(sql.contains(n("or_orden_banco = dt_orden_banco")) || sql.contains(n("dt_orden_banco = or_orden_banco")))
                .isTrue();
        verify(query).setObject(1, 123456, Types.INTEGER);
        verify(query).setObject(2, 2, Types.INTEGER);
        verify(query).setObject(3, 500, Types.INTEGER);
        assertThat(found).containsExactly(new SwiftCreditDetail("GRPFAKE01", "CUENTA UNO"),
                new SwiftCreditDetail("GRPFAKE02", null));
    }

    @Test
    void rule019_historySwiftDetailReadsTheSatHistoryTables() throws SQLException {
        queryReturns(rows(List.of("dt_referencia_grupo", "dt_nom_cuenta")));

        List<SwiftCreditDetail> found = session().historySwiftCreditDetails(123456, 0, 500);

        assertThat(onlySql()).contains(n("db_sat_his..bp_orden_his"), n("db_sat_his..bp_detalle_his"),
                n("dt_secuencial = ?"), n("or_ordenante = ?")).doesNotContain("db_biz_pagos");
        assertThat(found).isEmpty();
    }

    @Test
    void rule004_liveInterbankDetailJoinsTheBceCatalogueOnTheNineCharacterReference() throws SQLException {
        queryReturns(rows(List.of("ct_nom_catalogo", "dt_tipo_cta", "dt_numero_cuenta"),
                new Object[] {"000000017BANCO DESTINO FAKE", 4, "2200334455"},
                new Object[] {"000000017BANCO DESTINO FAKE", null, "9900"}));

        List<InterbankCreditDetail> found = session().liveInterbankCreditDetails(123456);

        String sql = onlySql();
        assertThat(sql).startsWith(n("select ct_nom_catalogo, dt_tipo_cta, dt_numero_cuenta"))
                .contains(n("db_biz_pagos..bp_detalle"), n("db_biz_admempresa..ba_tabla"),
                        n("db_biz_admempresa..ba_catalogo"), n("dt_orden_banco = ?"),
                        n("dt_referencia_grupo = substring(ct_nom_catalogo, 1, 9)"),
                        n("tb_nom_tabla = 'ad_cuentas_bce'"), n("tb_est_tabla = 'A'"), n("ct_est_catalogo = 'A'"))
                .doesNotContain("db_sat_his");
        assertCatalogueJoin(sql);
        verify(query).setObject(1, 123456, Types.INTEGER);
        assertThat(found).containsExactly(
                new InterbankCreditDetail("000000017BANCO DESTINO FAKE", 4, "2200334455"),
                new InterbankCreditDetail("000000017BANCO DESTINO FAKE", null, "9900"));
    }

    @Test
    void rule004_historyInterbankDetailReadsBpDetalleHis() throws SQLException {
        queryReturns(rows(List.of("ct_nom_catalogo", "dt_tipo_cta", "dt_numero_cuenta")));

        session().historyInterbankCreditDetails(123456);

        assertThat(onlySql()).contains(n("db_sat_his..bp_detalle_his"), n("tb_nom_tabla = 'ad_cuentas_bce'"),
                n("dt_referencia_grupo = substring(ct_nom_catalogo, 1, 9)")).doesNotContain("db_biz_pagos");
    }

    @Test
    void rule036_a8_liveBeneficiaryReadsTheUnqualifiedBpDetalleInTheHomeDatabase() throws SQLException {
        queryReturns(rows(List.of("dt_nombre_beneficiario", "dt_referencia_grupo"),
                new Object[] {"BENEFICIARIO FAKE", "GRP000001"}));

        List<BeneficiaryDetail> found = session().liveBeneficiaryDetails(123456);

        assertThat(onlySql()).startsWith(n("select dt_nombre_beneficiario, dt_referencia_grupo"))
                .contains(n("cobis_alt..bp_detalle"), n("dt_orden_banco = ?"))
                .doesNotContain("db_biz_pagos", "db_sat_his");
        verify(query).setObject(1, 123456, Types.INTEGER);
        assertThat(found).containsExactly(new BeneficiaryDetail("BENEFICIARIO FAKE", "GRP000001"));
    }

    @Test
    void rule036_historyBeneficiaryReadsBpDetalleHis() throws SQLException {
        queryReturns(rows(List.of("dt_nombre_beneficiario", "dt_referencia_grupo")));

        assertThat(session().historyBeneficiaryDetails(123456)).isEmpty();
        assertThat(onlySql()).contains(n("db_sat_his..bp_detalle_his"), n("dt_orden_banco = ?"));
    }

    // ---- account masters -----------------------------------------------------------------------------

    @Test
    void rule005_currentAccountClient() throws SQLException {
        queryReturns(rows(List.of("cc_cliente"), new Object[] {700001}));

        Optional<Integer> client = session().currentAccountClient(ACCOUNT);

        assertThat(onlySql()).isEqualTo(n("select cc_cliente from cob_cuentas..cc_ctacte where cc_cta_banco = ?"));
        verify(query).setString(1, ACCOUNT);
        assertThat(client).contains(700001);
    }

    @Test
    void rule005_savingsAccountClientAndANullClient() throws SQLException {
        queryReturns(rows(List.of("ah_cliente"), new Object[] {null}));

        Optional<Integer> client = session().savingsAccountClient(ACCOUNT);

        assertThat(onlySql()).isEqualTo(n("select ah_cliente from cob_ahorros..ah_cuenta where ah_cta_banco = ?"));
        assertThat(client).isEmpty();
    }

    @Test
    void rule005_virtualAccountOwnerKeepsTheRowEvenWithANullClient() throws SQLException {
        queryReturns(rows(List.of("vi_cliente", "vi_prod_banc"), new Object[] {null, 13}),
                rows(List.of("vi_cliente", "vi_prod_banc")));
        JdbcAseSession session = session();

        Optional<VirtualAccountOwner> withRow = session.virtualAccountOwner(ACCOUNT);
        Optional<VirtualAccountOwner> noRow = session.virtualAccountOwner(ACCOUNT);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, org.mockito.Mockito.times(2)).prepareStatement(sql.capture());
        assertThat(n(sql.getAllValues().get(0)))
                .isEqualTo(n("select vi_cliente, vi_prod_banc from cob_virtuales..vi_cuenta where vi_cta_banco = ?"));
        assertThat(withRow).contains(new VirtualAccountOwner(null, 13));
        assertThat(noRow).isEmpty();
    }

    @Test
    void aReadFailureIsAnAsePortException() throws SQLException {
        when(query.executeQuery()).thenThrow(new SQLException("gone", "HY000", 1));

        assertThatThrownBy(() -> session().smsServiceCodes("ROLPAGO", "SAT")).isInstanceOf(AsePortException.class);
    }

    // ---- notifiers -----------------------------------------------------------------------------------

    private static BasicNotificationCommand basicCommand() {
        return new BasicNotificationCommand("BNK", ACCOUNT, 3, "ROLPAGO", 123456, "GRP000001", 3,
                new BigDecimal("250.00"), "BENEFICIARIO FAKE", new BigDecimal("1.20"), "2200334455", "AHO", "BANCO FAKE");
    }

    @Test
    void rule036_a15_basicNotifierCallText15ParametersOErrorAt15BoundTo0() throws SQLException {
        when(call.getInt(1)).thenReturn(0);
        when(call.getObject(15)).thenReturn(5);
        when(call.getString(16)).thenReturn("AVISO FAKE");

        BasicNotificationResult result = session().notifyBasic(basicCommand());

        verify(connection).prepareCall("{? = call cobis_alt..pa_sat_pnotificacion(" + "?, ".repeat(14) + "?)}");
        verify(call).registerOutParameter(1, Types.INTEGER);
        verify(call).setString(2, "BNK");                              // @i_canal
        verify(call).setString(3, ACCOUNT);                            // @i_ctadebito
        verify(call).setObject(4, 3, Types.SMALLINT);                  // @i_tipctadeb
        verify(call).setString(5, "ROLPAGO");                          // @i_servicio
        verify(call).setObject(6, 123456, Types.INTEGER);              // @i_orden_banco
        verify(call).setString(7, "GRP000001");                        // @i_direccion_transf
        verify(call).setObject(8, 3, Types.INTEGER);                   // @i_secuencial
        verify(call).setBigDecimal(9, new BigDecimal("250.00"));       // @i_valor
        verify(call).setString(10, "BENEFICIARIO FAKE");               // @i_nombrecred
        verify(call).setBigDecimal(11, new BigDecimal("1.20"));        // @i_comision
        verify(call).setString(12, "2200334455");                      // @i_ctacred
        verify(call).setString(13, "AHO");                             // @i_prod_cre
        verify(call).setString(14, "BANCO FAKE");                      // @i_empresa
        verify(call).setInt(15, 0);                                    // @o_error in: the caller's @o_error, 0
        verify(call).registerOutParameter(15, Types.INTEGER);
        verify(call).registerOutParameter(16, Types.VARCHAR);          // @o_msg
        assertThat(result).isEqualTo(new BasicNotificationResult(0, 5, "AVISO FAKE"));
    }

    @Test
    void a15_basicNotifierNullOErrorStaysNull() throws SQLException {
        when(call.getInt(1)).thenReturn(40002);
        when(call.getObject(15)).thenReturn(null);

        assertThat(session().notifyBasic(basicCommand())).isEqualTo(new BasicNotificationResult(40002, null, null));
    }

    @Test
    void rule034_eventCallText14ParametersInLegacyOrder() throws SQLException {
        when(call.getInt(1)).thenReturn(30001);

        EventResult result = session().registerEvent(new EventCommand("I", "SAT", "ROL", 3, ACCOUNT, "100.00", ACCOUNT,
                "CTE", "2200334455", "AHO", 700001, "0.50", "BANCO FAKE", "SAT"));

        verify(connection).prepareCall("{? = call cob_internet..sp_eventos(" + "?, ".repeat(13) + "?)}");
        verify(call).setString(2, "I");                                // @i_operacion
        verify(call).setString(3, "SAT");                              // @i_canal
        verify(call).setString(4, "ROL");                              // @i_servicio
        verify(call).setObject(5, 3, Types.SMALLINT);                  // @i_producto
        verify(call).setString(6, ACCOUNT);                            // @i_cuenta
        verify(call).setString(7, "100.00");                           // @i_valor
        verify(call).setString(8, ACCOUNT);                            // @i_cta_deb
        verify(call).setString(9, "CTE");                              // @i_prod_deb
        verify(call).setString(10, "2200334455");                      // @i_cta_cre
        verify(call).setString(11, "AHO");                             // @i_prod_cre
        verify(call).setObject(12, 700001, Types.INTEGER);             // @i_cliente
        verify(call).setString(13, "0.50");                            // @i_costo
        verify(call).setString(14, "BANCO FAKE");                      // @i_empresa
        verify(call).setString(15, "SAT");                             // @i_desc_canal
        assertThat(result).isEqualTo(new EventResult(30001));
    }

    // A COBIS notifier reports a failure with raiserror + return code and the legacy caller carries on
    // (exec @w_return = ..., line 1248): a raised error is a status, not an exception (review Phase 4 H1).

    @Test
    void rule034_aNotifierRaisedErrorAnswersItsReturnStatus() throws SQLException {
        when(call.execute()).thenThrow(new SQLException("fake notifier error", "ZZZZZ", 30001));
        when(call.getInt(1)).thenReturn(40002);

        assertThat(session().notifyBasic(basicCommand())).isEqualTo(new BasicNotificationResult(40002, 0, null));
    }

    @Test
    void rule034_aNotifierRaisedErrorWithoutStatusAnswersTheErrorNumber() throws SQLException {
        when(call.execute()).thenThrow(new SQLException("fake notifier error", "ZZZZZ", 30001));

        assertThat(session().registerEvent(new EventCommand("I", "SAT", "ROL", 3, ACCOUNT, "100.00", ACCOUNT, "CTE",
                null, null, 700001, "0.00", null, "SAT"))).isEqualTo(new EventResult(30001));
    }

    @Test
    void aNotifierLosingTheTransactionStillEndsTheDebit() throws SQLException {
        java.sql.Statement statement = mock(java.sql.Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(call.execute()).thenThrow(new SQLException("deadlock victim", "40001", 1205));
        JdbcAseSession session = session();
        session.begin();

        assertThatThrownBy(() -> session.notifyBasic(basicCommand()))
                .isInstanceOf(com.nexti.debcred.AseTransactionAbortedException.class);
    }

    @Test
    void aNotifierPrepareFailureIsAnAsePortException() throws SQLException {
        when(connection.prepareCall(anyString())).thenThrow(new SQLException("boom", "HY000", 1));

        assertThatThrownBy(() -> session().notifyBasic(basicCommand())).isInstanceOf(AsePortException.class);
    }

    // ---- startup signature check ---------------------------------------------------------------------

    @Test
    void theStartupCheckKnowsBothNotifierSignatures() throws SQLException {
        assertThat(ProcedureSignatureCheck.PA_SAT_PNOTIFICACION).containsExactly("@i_canal", "@i_ctadebito",
                "@i_tipctadeb", "@i_servicio", "@i_orden_banco", "@i_direccion_transf", "@i_secuencial", "@i_valor",
                "@i_nombrecred", "@i_comision", "@i_ctacred", "@i_prod_cre", "@i_empresa", "@o_error", "@o_msg");
        assertThat(ProcedureSignatureCheck.SP_EVENTOS).containsExactly("@i_operacion", "@i_canal", "@i_servicio",
                "@i_producto", "@i_cuenta", "@i_valor", "@i_cta_deb", "@i_prod_deb", "@i_cta_cre", "@i_prod_cre",
                "@i_cliente", "@i_costo", "@i_empresa", "@i_desc_canal");

        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        List<String> swapped = new ArrayList<>(ProcedureSignatureCheck.SP_EVENTOS);
        java.util.Collections.swap(swapped, 5, 11);                    // @i_valor <-> @i_costo
        when(meta.getProcedureColumns("cob_internet", null, "sp_eventos", null)).thenAnswer(inv -> columns(swapped));
        assertThatThrownBy(() -> ProcedureSignatureCheck.verify(meta, "cob_internet", "sp_eventos",
                ProcedureSignatureCheck.SP_EVENTOS)).isInstanceOf(IllegalStateException.class);
        when(meta.getProcedureColumns("cobis_alt", null, "pa_sat_pnotificacion", null))
                .thenAnswer(inv -> columns(ProcedureSignatureCheck.PA_SAT_PNOTIFICACION));
        ProcedureSignatureCheck.verify(meta, "cobis_alt", "pa_sat_pnotificacion", ProcedureSignatureCheck.PA_SAT_PNOTIFICACION);
    }

    private static ResultSet columns(List<String> names) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        int[] row = {-1};
        when(rs.next()).thenAnswer(inv -> ++row[0] < names.size());
        when(rs.getShort("COLUMN_TYPE")).thenReturn((short) DatabaseMetaData.procedureColumnIn);
        when(rs.getString("COLUMN_NAME")).thenAnswer(inv -> names.get(row[0]));
        return rs;
    }

    @Test
    void theNormalizerKeepsDatabaseQualifiedNamesAndDropsAliases() {
        assertThat(n("select c.ct_cod_catalogo from db_biz_admempresa..ba_catalogo c where C.ct_est_catalogo = 'A'"))
                .isEqualTo("selectct_cod_catalogofromdb_biz_admempresa..ba_catalogocwherect_est_catalogo='a'");
        assertThat(Arrays.asList(n("cobis..bp_detalle"), n("x.y"))).containsExactly("cobis..bp_detalle", "y");
    }
}
