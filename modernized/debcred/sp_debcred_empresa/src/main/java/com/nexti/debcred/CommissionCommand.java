package com.nexti.debcred;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Arguments of {@code sp_grb_comision} in legacy order (lines 1518-1594 and 1746-1820).
 * {@code iTipoafec} is null on the first call (the parameter is omitted) and {@code "16"} on the SWIFT
 * call; the five REF33 breakdown fields after {@code iValorTarifa} are passed only on the first call.
 */
public record CommissionCommand(Integer sSsn, String sSrv, String sUser, String sTerm, Integer sOfi, String iAplcobis,
                                String iSpName, LocalDateTime iFechaProceso, String iCanalComision, Integer iEmpresa,
                                Integer iProducto, String iServicio, String iTipoProceso, Integer iOrdenBanco,
                                BigDecimal iValorComision, String iCadena, String iTarjeta, String iFrmPagcob,
                                Integer iMoneda, Integer iTipctaEmp, String iNumctaEmp, String iReferencia,
                                String iDetalleRef, String iTipoPagcob, Integer iPaisCta, Integer iCodBancoCta,
                                String iNemOrdenante, Integer iLocalidadPagcob, String iNombreCuenta,
                                String iNombreBeneficiario, Integer iOrdenEmpresa, String iTipoHorario,
                                String iTipoafec, String iSavepoint, int iSecuencial, BigDecimal iValorTarifa,
                                BigDecimal iValorComisionCue, BigDecimal iValorTarifaEfe, BigDecimal iValorComisionEfe,
                                BigDecimal iValorTarifaChe, BigDecimal iValorComisionChe) {
}
