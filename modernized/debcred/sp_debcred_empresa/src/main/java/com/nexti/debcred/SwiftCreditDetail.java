package com.nexti.debcred;

/** A TRANSWIFT detail row: {@code dt_referencia_grupo}, {@code dt_nom_cuenta} (raw). RULE-019. */
public record SwiftCreditDetail(String referenciaGrupo, String nomCuenta) {
}
