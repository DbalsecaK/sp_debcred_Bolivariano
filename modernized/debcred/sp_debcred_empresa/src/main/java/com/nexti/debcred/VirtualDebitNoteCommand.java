package com.nexti.debcred;

import java.math.BigDecimal;

/** Arguments of {@code sp_vi_ndc_automatica} in legacy order. {@code iCanal} is always {@code "SAT"}, {@code iVerfEstadoCta} {@code "S"}, {@code sOfi} 0. */
public record VirtualDebitNoteCommand(String sSrv, Integer sOfi, String sUser, String sTerm, Integer tTrn, String iCta,
                                      BigDecimal iVal, String iCau, Integer iMon, Integer iEmpresa, String iCanal,
                                      String iVerfEstadoCta, Boolean iBatch, String iRef, Integer iAlt) {
}
