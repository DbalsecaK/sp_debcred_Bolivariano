package com.nexti.debcred.support;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.nexti.debcred.DebitRequest;

/**
 * Builder of {@link DebitRequest} for the tests. Every value is a clearly fake fixture of the same
 * shape as the legacy one (account {@code 0000012345}, company 500, order 123456): no credential
 * or real customer data is copied from the legacy code.
 *
 * <p>The defaults describe the first slice of Phase 1: account type 3 (current account), service
 * ROLPAGO, channel DIR, affectation 10, aplcobis 'N', debit 100.00, no commission.
 */
public final class Requests {

    public static final LocalDateTime PROCESS_DATE = LocalDateTime.of(2026, 9, 27, 10, 30, 0);
    public static final String ACCOUNT = "0000012345";
    public static final int COMPANY = 500;
    public static final int BANK_ORDER = 123456;
    public static final int COMPANY_ORDER = 4587;

    private Integer sSsn = 77;
    private String sUser = "usrtest";
    private String sTerm = "TERM01";
    private String sSrv = "SRVTEST";
    private Integer sOfi = 1;
    private String iAplcobis = "N";
    private String iSpName = "sp_test_caller";
    private Integer iOrden = BANK_ORDER;
    private Integer iOrdenEmpresa = COMPANY_ORDER;
    private LocalDateTime iFechaProceso = PROCESS_DATE;
    private String iNemEmp = "EMPTEST";
    private Integer iEmpresa = COMPANY;
    private Integer iProducto = 1;
    private String iServicio = "ROLPAGO";
    private String iFrmPagcob = "CUE";
    private String iFrmPagcobSpi = null;
    private String iCanal = "DIR";
    private String iTipoAfec = "10";
    private String iReferencia = "REF TEST 001";
    private Integer iMonDebito = 1;
    private Integer iPaisCta = 1;
    private Integer iCodBancoCta = 34;
    private Integer iTipctaEmp = 3;
    private String iNumctaEmp = ACCOUNT;
    private BigDecimal iValorOrdenado = money("100.00");
    private BigDecimal iValorDebito = money("100.00");
    private BigDecimal iComision = money("0");
    private BigDecimal iValorComision = money("0");
    private String iTipoReferencia = "N";
    private String iTarjeta = null;
    private String iRefProv = "PROV-REF-01";
    private String iOpcion = "02";
    private String iTipoPagcob = "P";
    private Integer iLocalidadOrden = 1;
    private String iTipoProceso = "L";
    private String iFrmPagcobDeb = null;
    private BigDecimal iValor2Swift = money("0");
    private Integer iSecuencial = 0;
    private String iCodSwift = null;
    private BigDecimal iValorTarifa = null;
    private BigDecimal iValorComisionCue = null;
    private BigDecimal iValorTarifaEfe = null;
    private BigDecimal iValorComisionEfe = null;
    private BigDecimal iValorTarifaChe = null;
    private BigDecimal iValorComisionChe = null;

    private Requests() {
    }

    /** First slice: account type 3, ROLPAGO, DIR, aplcobis 'N', debit 100.00. */
    public static Requests currentAccount() {
        return new Requests();
    }

    public static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    public Requests sSsn(Integer v) { this.sSsn = v; return this; }
    public Requests sUser(String v) { this.sUser = v; return this; }
    public Requests sTerm(String v) { this.sTerm = v; return this; }
    public Requests sSrv(String v) { this.sSrv = v; return this; }
    public Requests sOfi(Integer v) { this.sOfi = v; return this; }
    public Requests aplcobis(String v) { this.iAplcobis = v; return this; }
    public Requests spName(String v) { this.iSpName = v; return this; }
    public Requests orden(Integer v) { this.iOrden = v; return this; }
    public Requests ordenEmpresa(Integer v) { this.iOrdenEmpresa = v; return this; }
    public Requests fechaProceso(LocalDateTime v) { this.iFechaProceso = v; return this; }
    public Requests nemEmp(String v) { this.iNemEmp = v; return this; }
    public Requests empresa(Integer v) { this.iEmpresa = v; return this; }
    public Requests producto(Integer v) { this.iProducto = v; return this; }
    public Requests servicio(String v) { this.iServicio = v; return this; }
    public Requests frmPagcob(String v) { this.iFrmPagcob = v; return this; }
    public Requests frmPagcobSpi(String v) { this.iFrmPagcobSpi = v; return this; }
    public Requests canal(String v) { this.iCanal = v; return this; }
    public Requests tipoAfec(String v) { this.iTipoAfec = v; return this; }
    public Requests referencia(String v) { this.iReferencia = v; return this; }
    public Requests monDebito(Integer v) { this.iMonDebito = v; return this; }
    public Requests paisCta(Integer v) { this.iPaisCta = v; return this; }
    public Requests codBancoCta(Integer v) { this.iCodBancoCta = v; return this; }
    public Requests tipctaEmp(Integer v) { this.iTipctaEmp = v; return this; }
    public Requests numctaEmp(String v) { this.iNumctaEmp = v; return this; }
    public Requests valorOrdenado(String v) { this.iValorOrdenado = money(v); return this; }
    public Requests valorDebito(String v) { this.iValorDebito = money(v); return this; }
    public Requests comision(String v) { this.iComision = v == null ? null : money(v); return this; }
    public Requests valorComision(String v) { this.iValorComision = v == null ? null : money(v); return this; }
    public Requests tipoReferencia(String v) { this.iTipoReferencia = v; return this; }
    public Requests tarjeta(String v) { this.iTarjeta = v; return this; }
    public Requests refProv(String v) { this.iRefProv = v; return this; }
    public Requests opcion(String v) { this.iOpcion = v; return this; }
    public Requests tipoPagcob(String v) { this.iTipoPagcob = v; return this; }
    public Requests localidadOrden(Integer v) { this.iLocalidadOrden = v; return this; }
    public Requests tipoProceso(String v) { this.iTipoProceso = v; return this; }
    public Requests frmPagcobDeb(String v) { this.iFrmPagcobDeb = v; return this; }
    public Requests valor2Swift(String v) { this.iValor2Swift = v == null ? null : money(v); return this; }
    public Requests secuencial(Integer v) { this.iSecuencial = v; return this; }
    public Requests codSwift(String v) { this.iCodSwift = v; return this; }
    public Requests valorTarifa(String v) { this.iValorTarifa = v == null ? null : money(v); return this; }
    public Requests valorComisionCue(String v) { this.iValorComisionCue = v == null ? null : money(v); return this; }
    public Requests valorTarifaEfe(String v) { this.iValorTarifaEfe = v == null ? null : money(v); return this; }
    public Requests valorComisionEfe(String v) { this.iValorComisionEfe = v == null ? null : money(v); return this; }
    public Requests valorTarifaChe(String v) { this.iValorTarifaChe = v == null ? null : money(v); return this; }
    public Requests valorComisionChe(String v) { this.iValorComisionChe = v == null ? null : money(v); return this; }

    public DebitRequest build() {
        return new DebitRequest(sSsn, sUser, sTerm, sSrv, sOfi,
                iAplcobis, iSpName, iOrden, iOrdenEmpresa, iFechaProceso, iNemEmp, iEmpresa, iProducto,
                iServicio, iFrmPagcob, iFrmPagcobSpi, iCanal, iTipoAfec, iReferencia, iMonDebito, iPaisCta,
                iCodBancoCta, iTipctaEmp, iNumctaEmp, iValorOrdenado, iValorDebito, iComision, iValorComision,
                iTipoReferencia, iTarjeta, iRefProv, iOpcion, iTipoPagcob, iLocalidadOrden, iTipoProceso,
                iFrmPagcobDeb, iValor2Swift, iSecuencial, iCodSwift, iValorTarifa, iValorComisionCue,
                iValorTarifaEfe, iValorComisionEfe, iValorTarifaChe, iValorComisionChe);
    }
}
