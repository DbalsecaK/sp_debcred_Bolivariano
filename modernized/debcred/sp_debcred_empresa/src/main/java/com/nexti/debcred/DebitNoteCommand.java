package com.nexti.debcred;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Arguments of {@code sp_ndc_ahcc} in legacy order (lines 676-730). {@code sOfi} is always 0, {@code iSecuencial} 0. */
public record DebitNoteCommand(Integer sSsn, String sSrv, String sUser, String sTerm, Integer sOfi, LocalDateTime sDate,
                               Integer tTrn, String iCuenta, Integer iTipoCuenta, String iCausal, BigDecimal iValor,
                               Integer iMon, String iTarjeta, String iServicio, String iRef, String iAplcobis,
                               String iDetalle, BigDecimal iTcomision, String iCanal, String iFrmPagcob, Integer iAlt,
                               String iAlternoDos, Integer iOrdenBanco, int iSecuencial, int iNchq, BigDecimal iSolca) {
}
