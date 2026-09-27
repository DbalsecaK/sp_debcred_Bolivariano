package com.nexti.debcred;

/** Arguments of {@code sp_con_comision} in legacy order; {@code codAlcance} is always {@code "E"}, {@code tipComision} {@code "01"}, {@code secuencia} 1. */
public record CommissionTariffQuery(Integer codEmpresa, Integer codProducto, String codServicio, String codCanal,
                                    String codAlcance, String tipComision, int secuencia, String aplcobis) {
}
