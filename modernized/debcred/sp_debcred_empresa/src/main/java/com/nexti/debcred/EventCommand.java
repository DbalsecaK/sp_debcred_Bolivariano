package com.nexti.debcred;

/** The arguments of {@code sp_eventos}, in the legacy order (1214-1242); value and cost travel as varchar(11). */
public record EventCommand(String iOperacion, String iCanal, String iServicio, Integer iProducto, String iCuenta,
                           String iValor, String iCtaDeb, String iProdDeb, String iCtaCre, String iProdCre,
                           Integer iCliente, String iCosto, String iEmpresa, String iDescCanal) {
}
