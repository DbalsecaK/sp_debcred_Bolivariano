package com.nexti.debcred;

/**
 * Arguments of {@code sp_con_confcontable} in legacy order. {@code concepto} null means the parameter
 * is omitted (the TRANSQUICK call with an SPI payment form, lines 322-344).
 */
public record AccountingConfigurationQuery(Integer producto, String servicio, String tipoafec, String frmPagcob,
                                           String canal, Integer tipcta, String moneda, String referencia,
                                           Integer empresa, String concepto) {
}
