package com.nexti.debcred;

/**
 * An interbank detail row joined to {@code ad_cuentas_bce}: the whole {@code ct_nom_catalogo},
 * {@code dt_tipo_cta}, {@code dt_numero_cuenta}. RULE-004.
 */
public record InterbankCreditDetail(String catalogName, Integer tipoCta, String numeroCuenta) {
}
