package com.nexti.debcred.ase;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails fast at startup when {@code sp_grb_comision} does not declare its parameters in the order the
 * positional call in {@link JdbcAseSession#charge} assumes (architecture review Phase 2 H1, Phase 1 M2).
 * The legacy calls it by name; the procedure's source is not available (brief section 7, A5), so the
 * declaration order is an assumption until this check has passed once against the bank's ASE.
 */
@Component
@Profile("ase")
class ProcedureSignatureCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcedureSignatureCheck.class);

    /** The order {@code JdbcAseSession.charge} binds; {@code @i_tipoafec} is omitted on the first call. */
    static final List<String> SP_GRB_COMISION = List.of(
            "@s_ssn", "@s_srv", "@s_user", "@s_term", "@s_ofi", "@i_aplcobis", "@i_sp_name", "@i_fecha_proceso",
            "@i_canal_comision", "@i_empresa", "@i_producto", "@i_servicio", "@i_tipo_proceso", "@i_orden_banco",
            "@i_valor_comision", "@i_cadena", "@i_tarjeta", "@i_frm_pagcob", "@i_moneda", "@i_tipcta_emp",
            "@i_numcta_emp", "@i_referencia", "@i_detalle_ref", "@i_tipo_pagcob", "@i_pais_cta", "@i_cod_banco_cta",
            "@i_nem_ordenante", "@i_localidad_pagcob", "@i_nombre_cuenta", "@i_nombre_beneficiario",
            "@i_orden_empresa", "@i_tipo_horario", "@i_tipoafec", "@i_savepoint", "@i_secuencial", "@o_error",
            "@i_valor_tarifa", "@i_valor_comision_cue", "@i_valor_tarifa_efe", "@i_valor_comision_efe",
            "@i_valor_tarifa_che", "@i_valor_comision_che");

    private final DataSource aseDataSource;
    private final AseProperties ase;

    ProcedureSignatureCheck(DataSource aseDataSource, AseProperties ase) {
        this.aseDataSource = aseDataSource;
        this.ase = ase;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = aseDataSource.getConnection()) {
            verify(connection.getMetaData(), ase.cobisDatabase(), "sp_grb_comision", SP_GRB_COMISION);
        }
    }

    static void verify(DatabaseMetaData meta, String database, String procedure, List<String> expected)
            throws SQLException {
        List<String> declared = new ArrayList<>();
        try (ResultSet columns = meta.getProcedureColumns(database, null, procedure, null)) {
            while (columns.next()) {
                if (columns.getShort("COLUMN_TYPE") != DatabaseMetaData.procedureColumnReturn) {
                    declared.add(columns.getString("COLUMN_NAME"));
                }
            }
        }
        if (!declared.subList(0, Math.min(declared.size(), expected.size())).equals(expected)) {
            throw new IllegalStateException(database + ".." + procedure + " declares its parameters as " + declared
                    + " but JdbcAseSession binds them as " + expected + ": fix the positional call before any debit");
        }
        log.info("{}..{} parameter order confirmed ({} parameters)", database, procedure, declared.size());
    }
}
