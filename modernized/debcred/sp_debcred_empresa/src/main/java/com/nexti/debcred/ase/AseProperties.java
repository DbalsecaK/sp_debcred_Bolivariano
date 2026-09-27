package com.nexti.debcred.ase;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the COBIS procedures and tables live. The values come from the environment
 * ({@code DEBCRED_ASE_URL}, {@code DEBCRED_ASE_USERNAME}, {@code DEBCRED_ASE_PASSWORD}); no
 * credential is kept in the repository.
 *
 * @param url            jTDS URL, {@code jdbc:jtds:sybase://host:port/database}
 * @param username       login
 * @param password       password
 * @param cobisDatabase  the database that holds the unqualified names of the legacy
 *                       ({@code sp_ndc_ahcc}, {@code sp_grb_mov_y_frmpgo}, {@code sp_grb_comision},
 *                       {@code pa_sat_pnotificacion}, {@code bp_total_orden}, {@code bp_detalle}); brief
 *                       section 7, A8: {@code cobis}
 */
@ConfigurationProperties("debcred.ase")
public record AseProperties(String url, String username, String password, String cobisDatabase) {

    public AseProperties {
        if (cobisDatabase == null || cobisDatabase.isBlank()) {
            cobisDatabase = "cobis";
        }
    }
}
