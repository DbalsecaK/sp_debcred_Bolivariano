package com.nexti.debcred;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Arguments of {@code sp_graba_tran_servicio} in legacy order. {@code sSsn} is passed null, {@code iIndicador} 1, {@code iOficinaCta} 0, {@code iSecuencial} 0. */
public record LedgerDebitCommand(String sSrv, Integer sOfi, Integer sSsn, String sUser, String sTerm, Integer tTrn,
                                 LocalDateTime iFecha, String iReferencia, String iCtaBanco, Integer iOficina,
                                 int iIndicador, Integer iMoneda, String iCausa, BigDecimal iSaldo, BigDecimal iValor,
                                 int iOficinaCta, String iTipoChequera, Integer iOrdenBanco, int iSecuencial) {
}
