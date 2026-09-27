package com.nexti.debcred;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Arguments of {@code sp_grb_mov_y_frmpgo} in legacy order (lines 1394-1460). {@code iEstProceso} is
 * {@code "P"} or {@code "X"}; {@code iTipoAfectacion} always {@code "1"}; {@code iNombreCuenta} and
 * {@code iNombreBeneficiario} are always null (the legacy never assigns {@code @w_nombre_cuenta}).
 */
public record MovementCommand(String sUser, String sTerm, Integer sOfi, Integer tTrn, String iTipoProceso,
                              Integer iEmpresa, Integer iProducto, Integer iOrdenBanco, String iCanal, String iCau,
                              String iEstProceso, Integer iCodError, String iFrmPagcob, Integer iMonedaOrden,
                              BigDecimal iValorMov, String iTipoAfectacion, LocalDateTime iFchContab,
                              String iReferencia, int iSecuencial, String iServicio, String iTipoPagcob,
                              Integer iPaisCta, Integer iCodBancoCta, Integer iTipoCta, String iNumeroCta,
                              BigDecimal iValorOrdenado, String iNemOrdenante, Integer iLocalidadPagcob,
                              String iNombreCuenta, String iNombreBeneficiario, Integer iOrdenEmpresa,
                              BigDecimal iValorComision, Integer iTranNcnd) {
}
