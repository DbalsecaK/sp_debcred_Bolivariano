package com.nexti.debcred;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The 45 input parameters of {@code dbo.sp_debcred_empresa} (sp_debcred_empresa.sp:4-96), in the
 * procedure's order. Money is {@link BigDecimal}; a null component is a T-SQL NULL.
 *
 * <p>RULE-037: the legacy parameter defaults ({@code @i_aplcobis 'N'}, {@code @i_canal 'DIR'},
 * {@code @i_tipo_afec '10'}, {@code @i_valor2_swift 0}, {@code @i_secuencial 0}) are applied here when
 * the component is null, because a REST request cannot tell "omitted" from "null" (approved reading,
 * brief section 7). The other defaulted parameters ({@code @s_ssn}, {@code @s_term},
 * {@code @i_frm_pagcob_spi}, {@code @i_tarjeta}, {@code @i_cod_swift}, {@code @i_frm_pagcob_deb} and the
 * six REF33 fields) stay null: the procedure handles them itself.
 *
 * <p>The request is immutable. The legacy mutates several of its parameters as working variables;
 * {@link DebitCompanyAccountService} keeps those as locals, so what a port receives is observable
 * without the request changing.
 */
public record DebitRequest(
        Integer sSsn, String sUser, String sTerm, String sSrv, Integer sOfi,
        String iAplcobis, String iSpName, Integer iOrden, Integer iOrdenEmpresa,
        LocalDateTime iFechaProceso, String iNemEmp, Integer iEmpresa, Integer iProducto,
        String iServicio, String iFrmPagcob, String iFrmPagcobSpi, String iCanal, String iTipoAfec,
        String iReferencia, Integer iMonDebito, Integer iPaisCta, Integer iCodBancoCta,
        Integer iTipctaEmp, String iNumctaEmp,
        BigDecimal iValorOrdenado, BigDecimal iValorDebito, BigDecimal iComision, BigDecimal iValorComision,
        String iTipoReferencia, String iTarjeta, String iRefProv, String iOpcion, String iTipoPagcob,
        Integer iLocalidadOrden, String iTipoProceso, String iFrmPagcobDeb,
        BigDecimal iValor2Swift, Integer iSecuencial, String iCodSwift,
        BigDecimal iValorTarifa, BigDecimal iValorComisionCue, BigDecimal iValorTarifaEfe,
        BigDecimal iValorComisionEfe, BigDecimal iValorTarifaChe, BigDecimal iValorComisionChe) {

    public static final String DEFAULT_APLCOBIS = "N";
    public static final String DEFAULT_CANAL = "DIR";
    public static final String DEFAULT_TIPO_AFEC = "10";

    public DebitRequest {
        if (iAplcobis == null) {
            iAplcobis = DEFAULT_APLCOBIS;
        }
        if (iCanal == null) {
            iCanal = DEFAULT_CANAL;
        }
        if (iTipoAfec == null) {
            iTipoAfec = DEFAULT_TIPO_AFEC;
        }
        if (iValor2Swift == null) {
            iValor2Swift = BigDecimal.ZERO;
        }
        if (iSecuencial == null) {
            iSecuencial = 0;
        }
        requireMoney("iValorOrdenado", iValorOrdenado);
        requireMoney("iValorDebito", iValorDebito);
        requireMoney("iComision", iComision);
        requireMoney("iValorComision", iValorComision);
        requireMoney("iValor2Swift", iValor2Swift);
        requireMoney("iValorTarifa", iValorTarifa);
        requireMoney("iValorComisionCue", iValorComisionCue);
        requireMoney("iValorTarifaEfe", iValorTarifaEfe);
        requireMoney("iValorComisionEfe", iValorComisionEfe);
        requireMoney("iValorTarifaChe", iValorTarifaChe);
        requireMoney("iValorComisionChe", iValorComisionChe);
    }

    /** The largest ASE {@code money} value; the smallest is its negative. */
    static final BigDecimal MONEY_MAX = new BigDecimal("922337203685477.5807");
    /** No SQL numeric type holds more decimals than this. */
    static final int MAX_DECIMALS = 38;

    /**
     * Harden JSEC-003 (brief section 7 A21): a value the legacy's {@code money} parameters could never hold
     * is refused (HTTP 400) before anything runs. Scale and precision are checked first, so a JSON number
     * such as {@code 1E999999999} is rejected without being expanded. The message never echoes the value.
     */
    private static void requireMoney(String name, BigDecimal value) {
        if (value == null) {
            return;
        }
        boolean fits = value.scale() <= MAX_DECIMALS
                && value.precision() - value.scale() <= 15                 // at most 15 integer digits
                && value.abs().compareTo(MONEY_MAX) <= 0;
        if (!fits) {
            throw new IllegalArgumentException(name + " does not fit an ASE money value");
        }
    }
}
