# Production contract for `com.nexti.debcred` (Phase 1 + Phase 2 + Phase 3 + Phase 4)

The characterization tests under this directory compile against exactly the types below. The
implementation is written to match this file; if something here must change, change the tests in
the same commit. Legacy oracle: `legacy/debcred/sp_debcred_empresa.sp` (line numbers below are
from that file). Assumed contracts of the called procedures: `analysis/debcred/DATA_OBJECTS.md`.

Everything is in package `com.nexti.debcred` unless stated. All records are Java `record`s. Money
is `java.math.BigDecimal`; `null` means T-SQL NULL. `Integer`/`Boolean` wrappers are used where the
legacy value may be NULL.

## 1. Request and result

### `DebitRequest` (45 input parameters, procedure order, lines 4-96 minus the two outputs)

```java
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
    BigDecimal iValorComisionEfe, BigDecimal iValorTarifaChe, BigDecimal iValorComisionChe) { ... }
```

Legacy parameter defaults (lines 14, 36, 38, 76, 78) are applied in the **compact constructor when
the field is null**: `iAplcobis -> "N"`, `iCanal -> "DIR"`, `iTipoAfec -> "10"`,
`iValor2Swift -> 0`, `iSecuencial -> 0`. (A REST request cannot tell "omitted" from "null"; this
is the approved reading of RULE-037. `sSsn`, `sTerm`, `iFrmPagcobSpi`, `iTarjeta`, `iCodSwift`,
`iFrmPagcobDeb` and the six REF33 fields stay null when null: the procedure handles them itself.)

The request is immutable. The legacy mutates its own parameters (`@i_comision`, `@i_valor_comision`,
`@s_term`, `@i_valor_debito`, `@i_servicio`, `@i_frm_pagcob_deb`, `@s_ssn`): the tests observe those
mutations only through the arguments the ports receive.

### `DebitResult`

```java
public record DebitResult(int returnCode, Integer oError, Integer oRegAProc) {}
```

`oRegAProc` is always `null` (never assigned by the legacy, brief section 7 A11).

### Entry point

```java
public class DebitCompanyAccountService {
    // Idiomatic wiring (architecture review M5): AseSession = AseTransaction + the ten ports + AutoCloseable.
    public DebitCompanyAccountService(AseSession ase, NotificationStep n, CommissionStep c, OrderHeaderStep o) { ... }
    public DebitCompanyAccountService(AseTransaction tx,
                                      CommissionTariffPort commissionTariff,
                                      AccountingConfigurationPort accountingConfiguration,
                                      DebitNotePort debitNote,
                                      VirtualDebitNotePort virtualDebitNote,
                                      LedgerDebitPort ledgerDebit,
                                      MovementPort movement,
                                      ErrorReportingPort errorReporting,
                                      NotificationStep notification,
                                      CommissionStep commission,
                                      OrderHeaderStep orderHeader,
                                      CatalogReader catalog,
                                      VirtualAccountReader virtualAccounts,
                                      OrderReader orders) { ... }

    public DebitResult debit(DebitRequest request);
}
```

The service never throws for a business outcome. A checked/unchecked failure of a port is modelled
as `AsePortException extends RuntimeException` (the equivalent of `@@error <> 0`); the service maps
it exactly where the legacy tests `@@error` (accounting lookup line 382 -> 120000; movement write
line 1464 -> 122002). Any other place a port throws, the exception propagates.

## 2. Transaction

```java
public interface AseTransaction {
    void begin();                              // begin tran            (line 402)
    void savepoint(String name);               // save tran @w_savepoint (line 408)
    void rollbackToSavepoint(String name);     // rollback tran @w_savepoint (line 1338)
    void rollback();                           // rollback tran          (line 2144)
    void commit();                             // commit tran           (lines 1486, 2130)
}
```

Savepoint name literal: `"sp_debito_empresa"`. `rollback()` is called in `lbl_error` **only if a
transaction is open** (`@@trancount > 0`): a 120000 raised before `begin()` produces neither
`begin()` nor `rollback()`.

## 3. Ports (one per called procedure) and their argument records

All argument records carry the legacy parameters in the legacy order with camelCase names. A field
the legacy passes as a literal is still a field (the tests assert the literal).

```java
public interface CommissionTariffPort {                              // db_biz_admempresa..sp_con_comision (204-222)
    CommissionTariffResult consult(CommissionTariffQuery q);
}
public record CommissionTariffQuery(Integer codEmpresa, Integer codProducto, String codServicio,
        String codCanal, String codAlcance /*'E'*/, String tipComision /*'01'*/, int secuencia /*1*/,
        String aplcobis) {}
public record CommissionTariffResult(int returnCode, BigDecimal valorPorTran) {}

public interface AccountingConfigurationPort {                      // db_biz_admempresa..sp_con_confcontable (322-374, 500-524)
    AccountingConfiguration resolve(AccountingConfigurationQuery q);
}
public record AccountingConfigurationQuery(Integer producto, String servicio, String tipoafec,
        String frmPagcob, String canal, Integer tipcta, String moneda, String referencia,
        Integer empresa, String concepto /* null = parameter omitted (TRANSQUICK+SPI form) */) {}
public record AccountingConfiguration(int returnCode, Integer trn, String causal) {}

public interface DebitNotePort {                                     // cobis..sp_ndc_ahcc (676-730)
    DebitNoteResult debit(DebitNoteCommand c);
}
public record DebitNoteCommand(Integer sSsn, String sSrv, String sUser, String sTerm, Integer sOfi /*0*/,
        LocalDateTime sDate, Integer tTrn, String iCuenta, Integer iTipoCuenta, String iCausal,
        BigDecimal iValor, Integer iMon, String iTarjeta, String iServicio, String iRef, String iAplcobis,
        String iDetalle, BigDecimal iTcomision, String iCanal, String iFrmPagcob /*= iFrmPagcobSpi*/,
        Integer iAlt, String iAlternoDos /*= iFrmPagcob*/, Integer iOrdenBanco, int iSecuencial /*0*/,
        int iNchq, BigDecimal iSolca) {}
public record DebitNoteResult(int returnCode, Integer transaccion) {}

public interface VirtualDebitNotePort {                              // cob_virtuales..sp_vi_ndc_automatica (628-662)
    VirtualDebitNoteResult debit(VirtualDebitNoteCommand c);
}
public record VirtualDebitNoteCommand(String sSrv, Integer sOfi /*0*/, String sUser, String sTerm,
        Integer tTrn, String iCta, BigDecimal iVal, String iCau, Integer iMon, Integer iEmpresa,
        String iCanal /*'SAT'*/, String iVerfEstadoCta /*'S'*/, Boolean iBatch, String iRef, Integer iAlt) {}
public record VirtualDebitNoteResult(int returnCode, Integer error, Integer ssnMonet) {}
// The debit error code is the RETURN VALUE (line 666), never `error`.

public interface LedgerDebitPort {                                   // db_biz_pagos..sp_graba_tran_servicio (1284-1298)
    LedgerDebitResult debit(LedgerDebitCommand c);
}
public record LedgerDebitCommand(String sSrv, Integer sOfi, Integer sSsn /*null*/, String sUser, String sTerm,
        Integer tTrn, LocalDateTime iFecha, String iReferencia /*= iRefProv*/, String iCtaBanco,
        Integer iOficina /*= sOfi*/, int iIndicador /*1*/, Integer iMoneda, String iCausa,
        BigDecimal iSaldo, BigDecimal iValor, int iOficinaCta /*0*/, String iTipoChequera /*empresa as text*/,
        Integer iOrdenBanco, int iSecuencial /*0*/) {}
public record LedgerDebitResult(int returnCode) {}

public interface MovementPort {                                      // cobis..sp_grb_mov_y_frmpgo (1394-1460)
    MovementResult record(MovementCommand c);
}
public record MovementCommand(String sUser, String sTerm, Integer sOfi, Integer tTrn, String iTipoProceso,
        Integer iEmpresa, Integer iProducto, Integer iOrdenBanco, String iCanal, String iCau,
        String iEstProceso /*'P'|'X'*/, Integer iCodError, String iFrmPagcob, Integer iMonedaOrden,
        BigDecimal iValorMov, String iTipoAfectacion /*'1'*/, LocalDateTime iFchContab, String iReferencia,
        int iSecuencial /*0*/, String iServicio, String iTipoPagcob, Integer iPaisCta, Integer iCodBancoCta,
        Integer iTipoCta, String iNumeroCta, BigDecimal iValorOrdenado, String iNemOrdenante,
        Integer iLocalidadPagcob, String iNombreCuenta /*null*/, String iNombreBeneficiario /*null*/,
        Integer iOrdenEmpresa, BigDecimal iValorComision /*= iComision after normalization*/,
        Integer iTranNcnd) {}
public record MovementResult(int returnCode) {}

public interface ErrorReportingPort {                                // cobis..sp_cerror (2152-2156)
    void report(ErrorReport r);
}
public record ErrorReport(String from /*= iSpName*/, int num) {}

public interface CatalogReader {                                     // ba_tabla/ba_catalogo (484-496)
    Optional<String> ndcorpeiConcept();   // ct_cod_catalogo of the active ad_concepto_contable row flagged NDCORPEI
}
public interface VirtualAccountReader {                              // cob_virtuales..vi_cuenta (300-304)
    boolean isBasicAccount(String ctaBanco);   // exists row with vi_cta_banco = ctaBanco and vi_prod_banc = 13
}
public interface OrderReader {                                       // bp_orden / db_sat_his..bp_orden_his (1352-1368)
    Optional<String> liveService(Integer ordenBanco);      // or_servicio from db_biz_pagos..bp_orden
    Optional<String> historyService(Integer ordenBanco);   // or_servicio from db_sat_his..bp_orden_his
}
```

### Steps stubbed in Phase 1

```java
public interface NotificationStep {                                  // B7, lines 746-1256, Phase 4
    NotificationOutcome notify(NotificationContext ctx);
}
public record NotificationContext(DebitRequest request, String servicio, Integer trn, String causal,
        BigDecimal valorDebito, BigDecimal comision, BigDecimal valorComision, Integer tranNcnd) {}
public record NotificationOutcome(boolean configured, int returnCode, BigDecimal valorDebito) {
    public static NotificationOutcome notConfigured() { return new NotificationOutcome(false, 0, null); }
}
```
Called only when the account type is 3, 4 or 12, the debit returned 0 and the trimmed service is not
`PAGOPRV` (line 746). When `configured == false` the service leaves the debit error code and the
debit value untouched (no SMS mapping: line 814 false, line 1248 not reached). When `configured`
is true the service sets the debit error code to `returnCode` (line 1248) and, if `valorDebito` is
non-null, replaces the working debit value (RULE-003, Phase 4). Phase 1 wires a stub that always
returns `notConfigured()`.

```java
public interface CommissionStep {                                    // B10/B11, lines 1500-1856, Phase 2
    int apply(CommissionContext ctx);      // 0 = continue; Phase 1 stub returns 0
}
public record CommissionContext(DebitRequest request, BigDecimal comision, BigDecimal valorComision,
        String frmPagcob, Integer trn, String causal, String savepoint /*"sp_debito_empresa"*/) {}

public interface OrderHeaderStep {                                   // B12, lines 1876-2090, Phase 3
    int update(OrderHeaderContext ctx);    // 0 = continue; Phase 1 stub returns 0
}
public record OrderHeaderContext(DebitRequest request, String servicio, String frmPagcobDeb,
        String actTotord /*'S'|'N'*/, Integer codErrord) {}
```
Both are reached only on the happy path (debit and movement succeeded), in this order, after the
movement write and before `commit()`. A non-zero return from either is out of Phase 1 scope (the
stubs return 0).

## 4. Behavior the tests pin (working-variable semantics)

Order of operations, exactly as the legacy:

1. `batch`: `"S" -> false`, `"N" -> true`, anything else -> `null` (lines 176-178).
2. Normalization (188-196): if `iComision > 0` and `iValorComision` **equals 0** (NULL does not
   trigger) then `valorComision = iComision`, `comision = 0`.
3. Tariff lookup (204-226): query with alcance `'E'`, tipComision `'01'`, secuencia `1`; if
   `returnCode != 0` then `valPorTran = 0`.
4. `comisionUnd = comision` if `comision > 0`, then `= valorComision` if `valorComision > 0`
   (nulls read as 0). If `valPorTran > 0`, `cantidad = trunc(comisionUnd / valPorTran)`: money
   division to 4 decimals (HALF_UP) then truncation toward zero to `int`. Finally
   `comisionUnd = valPorTran` unconditionally (line 248, confirmed as-is).
5. `moneda = convert(char(2), iMonDebito)`: the decimal text of `iMonDebito` right-padded with
   blanks to 2 characters (`1 -> "1 "`, `10 -> "10"`); a NULL `iMonDebito` gives a NULL `moneda`
   (T-SQL convert of NULL), never the text "null" (architecture review M3).
6. `oError = 0`; `actTotord = "S"`; `frmPagcobDeb = iFrmPagcobDeb ?? iFrmPagcob`; `sSsn = sSsn ?? 0`.
7. Concept (278-312): if `iServicio` equals `"SPI"` (trailing blanks ignored): `conceptoBas = sTerm`
   (the original value, may be null) and the working `sTerm` becomes `" "` for every later port.
   Else `conceptoBas = "0"`, and if `iTipctaEmp == 12` and `virtualAccounts.isBasicAccount(iNumctaEmp)`
   then `"91"`. `isBasicAccount` is consulted only for type 12.
8. Accounting lookup (320-374): if `iServicio` equals `"TRANSQUICK"` and `iFrmPagcobSpi != null`,
   query with `frmPagcob = iFrmPagcobSpi` and `concepto = null`; else `frmPagcob = iFrmPagcob`,
   `concepto = conceptoBas`. Other fields: `producto, servicio, tipoafec, canal, tipcta, moneda,
   referencia = iTipoReferencia, empresa`. `trn`/`causal` are taken from the result as returned
   (nulls allowed). If `returnCode > 0` or the port throws `AsePortException` -> `numError = 120000`,
   go to lbl_error (no `begin()` has happened).
9. `begin()`; `savepoint("sp_debito_empresa")`.
10. If `iTipctaEmp` in (3, 4, 12):
    - `cadena = convert(varchar, iOrdenEmpresa)` (NULL stays NULL); if trimmed service is `TRANSWIFT`,
      `cadena = "COD:" + iCodSwift` where a null `iCodSwift` yields a **null** cadena (T-SQL
      concatenation with NULL).
    - If trimmed service is `IMPADUAN`: `codSwift = iCodSwift ?? ""`; if `codSwift.trim()`
      equals `CORPEI`: `concepto = catalog.ndcorpeiConcept().orElse("99999")`, `causal = null`,
      second accounting query with `frmPagcob = iFrmPagcob` and that `concepto` (all other fields
      as in step 8); `trn` and `causal` taken from that result; `causal = causal ?? "512"`; if that
      `returnCode > 0` -> 120000 via lbl_error (now **with** `rollback()` because a transaction is
      open). A thrown `AsePortException` here is NOT mapped (legacy checks only `@w_return`).
    - If trimmed service is `SPI` and `iTipoAfec` equals `"12"`: `cadena = String.valueOf(iOrdenEmpresa)`.
    - Type 12: `VirtualDebitNoteCommand(sSrv, 0, sUser, sTerm, trn, iNumctaEmp, iValorDebito, causal,
      iMonDebito, iEmpresa, "SAT", "S", batch, cadena, iOrden)`; `codErrord = result.returnCode()`;
      `tranNcnd = result.ssnMonet()`.
    - Types 3/4: `DebitNoteCommand(sSsn, sSrv, sUser, sTerm, 0, iFechaProceso, trn, iNumctaEmp,
      iTipctaEmp, causal, iValorDebito, iMonDebito, iTarjeta, iServicio, cadena, iAplcobis, iRefProv,
      comision, iCanal, iFrmPagcobSpi, iOrden, iFrmPagcob, iOrden, 0, cantidad, comisionUnd)`;
      `codErrord = result.returnCode()`; `tranNcnd = result.transaccion()`.
    - Notification step as in section 3 when `codErrord == 0` and trimmed service != `PAGOPRV`.
11. Else if `iTipctaEmp == 9`: `empresaStr = convert(varchar(10), iEmpresa)` (NULL stays NULL); `saldo = null`,
    `valor = iValorDebito`; if `empresaStr.equals("1295")` and `iServicio` equals `"SPI"` then
    `saldo = iValorDebito`, `valor = 0` and the **working debit value stays 0** for the movement.
    `LedgerDebitCommand(sSrv, sOfi, null, sUser, sTerm, trn, iFechaProceso, iRefProv, iNumctaEmp,
    sOfi, 1, iMonDebito, causal, saldo, valor, 0, empresaStr, iOrden, 0)`; `codErrord = returnCode`.
    No notification, no basic-account check, no CORPEI re-resolution for type 9.
12. Else (any other type, including null): `codErrord = 122001`, no debit port called.
13. `stsProc = "P"`; if `codErrord != 0`: `rollbackToSavepoint("sp_debito_empresa")`, `stsProc = "X"`.
14. If trimmed service is `SPI` and `iTipoAfec` equals `"12"`: `servicio = orders.liveService(iOrden)`
    else `orders.historyService(iOrden)` (history consulted only when live is empty) else `null`;
    `actTotord = "N"`. Otherwise `servicio = iServicio` unchanged.
15. `MovementCommand(sUser, sTerm, sOfi, trn, iTipoProceso, iEmpresa, iProducto, iOrden, iCanal, causal,
    stsProc, codErrord, iFrmPagcob, iMonDebito, valorDebito, "1", iFechaProceso, iReferencia, 0,
    servicio, iTipoPagcob, iPaisCta, iCodBancoCta, iTipctaEmp, iNumctaEmp, iValorOrdenado, iNemEmp,
    iLocalidadOrden, null, null, iOrdenEmpresa, comision, tranNcnd)`. If `returnCode != 0` or the port
    throws `AsePortException` -> `numError = 122002`, lbl_error.
16. **Exit A** (line 1480): if `codErrord != 0`: `commit()`, `oError = codErrord`,
    return `DebitResult(codErrord, codErrord, null)`. Commission and order-header steps NOT reached.
17. Happy path: `commission.apply(...)` then `orderHeader.update(...)`, `commit()`,
    return `DebitResult(0, 0, null)`.
18. **Exit C** (`lbl_error`, 2140-2170): `rollback()` only if `begin()` happened. If `iAplcobis`
    equals `"S"`: `errorReporting.report(new ErrorReport(iSpName, numError))` and return
    `DebitResult(numError, 0, null)` (**`oError` stays at the 0 set in step 6**: the legacy does not
    assign it on this branch). Otherwise return `DebitResult(0, numError, null)` with no report.

String equality against the legacy literals ignores trailing blanks (Sybase `=` semantics on
char/varchar); the comparisons written with `ltrim(rtrim(...))` (TRANSWIFT, IMPADUAN, CORPEI,
SPI-at-558/1348, PAGOPRV) also ignore leading blanks. The tests use `Strings.aseEquals` semantics:
`"TRANSWIFT ".equals("TRANSWIFT")` is true for the `=` test; `" TRANSWIFT"` matches only the
trimmed ones.

## 5. Not in Phase 1

Commission debits (B10/B11), order-header update (B12), notifications (B7) and the REST facade.
The stubs above exist so the flow can be shown to reach them. The stray result set at line 882 and
`@o_reg_a_proc` are approved differences (brief section 7 A11).

## Phase 2: commission debits (B10/B11, lines 1496-1856)

The stubbed `CommissionStep` of section 3 becomes a real step. Everything else in this file stays.
Legacy oracle: lines 1496-1856; assumed contract of `sp_grb_comision`: `DATA_OBJECTS.md`,
"CommissionDebitPayload". Business logic preserved as-is; brief section 7 A14 approved.

### New port and records

```java
public interface CommissionPort {                                    // cobis..sp_grb_comision (1518, 1746)
    CommissionResult charge(CommissionCommand c);
}
public record CommissionCommand(Integer sSsn, String sSrv, String sUser, String sTerm, Integer sOfi, String iAplcobis,
        String iSpName, LocalDateTime iFechaProceso, String iCanalComision, Integer iEmpresa, Integer iProducto,
        String iServicio, String iTipoProceso, Integer iOrdenBanco, BigDecimal iValorComision, String iCadena,
        String iTarjeta, String iFrmPagcob, Integer iMoneda, Integer iTipctaEmp, String iNumctaEmp, String iReferencia,
        String iDetalleRef, String iTipoPagcob, Integer iPaisCta, Integer iCodBancoCta, String iNemOrdenante,
        Integer iLocalidadPagcob, String iNombreCuenta /*null*/, String iNombreBeneficiario /*null*/,
        Integer iOrdenEmpresa, String iTipoHorario /*= iTipoReferencia*/, String iTipoafec /*null on the first call, "16" on the SWIFT call*/,
        String iSavepoint, int iSecuencial /*0*/, BigDecimal iValorTarifa, BigDecimal iValorComisionCue,
        BigDecimal iValorTarifaEfe, BigDecimal iValorComisionEfe, BigDecimal iValorTarifaChe, BigDecimal iValorComisionChe) {}
public record CommissionResult(int returnCode, Integer oError) {}
```

`AseSession` also extends `CommissionPort` (the eleventh procedure of the same connection). A
`null` `oError` reads as 0: the `@o_error output` variable keeps the value it held before the call,
which on this path is always 0 (`@w_cod_errord` is 0 whenever the step is reached, and the second
call is only reached when the first left it at 0).

### The step

```java
public interface CommissionStep {
    CommissionOutcome apply(CommissionContext ctx);
}
public sealed interface CommissionOutcome {
    record Continue(String frmPagcob) implements CommissionOutcome {}   // the working payment form after the TRANSQUICK swap
    record ExitB(int oError)        implements CommissionOutcome {}   // step already rolled back and wrote the 'X' movement
    record LblError(int numError)   implements CommissionOutcome {}   // service runs exit C
}
public record CommissionContext(DebitRequest request, BigDecimal comision, BigDecimal valorComision,
        String frmPagcob, Integer trn, String causal, String savepoint /*"sp_debito_empresa"*/, Integer tranNcnd,
        String cadena, String servicio, String terminal, Integer sSsn) {}
public final class CommissionDebits implements CommissionStep {
    public CommissionDebits(CommissionPort commissions, MovementPort movements, AseTransaction tx) { ... }
}
```

`CommissionContext` carries the **working** values of the legacy variables the block reads, not the
request's: `comision`/`valorComision` after normalization (188-196), `trn`/`causal` as the debit used
them (after the CORPEI re-resolution), `tranNcnd` (`@w_tran_ncnd`, null for type 9), `cadena`
(`@w_cadena`, the Phase 1 reference string: `"COD:"+swift`, the company order as text, or null for
type 9 where it is never assigned), `servicio` (`@i_servicio` after the SPI-return lookup, step 14),
`terminal` (`@s_term`, `" "` for SPI, step 7) and `sSsn` (`@s_ssn ?? 0`, step 6). The four extra
components after `tranNcnd` (`cadena`, `servicio`, `terminal`, `sSsn`) are needed because lines
1520-1550, 1630-1668 and 1748-1778 read those mutated variables; the tests observe them only through
the port arguments.

The service (step 17 of section 4) now does, in place of the stub call:

```java
switch (commission.apply(ctx)) {
    case ExitB b      -> return new DebitResult(b.oError(), b.oError(), null);   // no commit (1698-1700)
    case LblError e   -> return errorExit(request, e.numError(), true);          // exit C, rollback, 1834
    case Continue c   -> orderHeader.update(...); tx.commit(); return new DebitResult(0, 0, null);
}
```

### Behavior pinned (order exactly as the legacy)

1. `frmPagcob = ctx.frmPagcob()`; if `servicio` equals `"TRANSQUICK"` (trailing blanks ignored) and
   `iFrmPagcobSpi != null` then `frmPagcob = iFrmPagcobSpi` for **everything after**: the first
   commission command, the exit-B movement and `Continue.frmPagcob` (1500-1502, RULE-025).
2. `valorTarifa = iValorTarifa`; if `servicio` equals `"TRANSBIMO"` then `valorTarifa = valorComision`
   (the normalized one; a null `valorComision` gives a null tariff) (1506-1508, RULE-001).
3. **First commission** (1512-1600), only if `valorComision > 0` (null or 0 skips the whole block):
   `CommissionCommand(sSsn, sSrv, sUser, terminal, sOfi, iAplcobis, iSpName, iFechaProceso, iCanal,
   iEmpresa, iProducto, servicio, iTipoProceso, iOrden, valorComision, cadena, iTarjeta, frmPagcob,
   iMonDebito, iTipctaEmp, iNumctaEmp, "COBRO DE COMISION", iRefProv, iTipoPagcob, iPaisCta,
   iCodBancoCta, iNemEmp, iLocalidadOrden, null, null, iOrdenEmpresa, iTipoReferencia, null /*tipoafec*/,
   "sp_debito_empresa", 0, valorTarifa, iValorComisionCue, iValorTarifaEfe, iValorComisionEfe,
   iValorTarifaChe, iValorComisionChe)`. RULE-013, RULE-039.
4. **Exit B** (1606-1700, REF44): if `returnCode != 0` **or** the port threw `AsePortException`
   **or** `oError != 0`: `oError` is the procedure's output (0 after a throw or when it returned
   0 with a non-zero return value); `tx.rollback()` (the transaction is always open here); then
   `movements.record(MovementCommand(sUser, terminal, sOfi, trn, iTipoProceso, iEmpresa, iProducto,
   iOrden, iCanal, causal, "X", oError, frmPagcob, iMonDebito, valorComision, "1", iFechaProceso,
   "COBRO DE COMISION", 0, servicio, iTipoPagcob, iPaisCta, iCodBancoCta, iTipctaEmp, iNumctaEmp,
   iValorOrdenado, iNemEmp, iLocalidadOrden, null, null, iOrdenEmpresa, comision, tranNcnd))`
   **outside any transaction** (autocommit); its return value is ignored; return `ExitB(oError)`.
   The service returns `DebitResult(oError, oError, null)` with **no commit** and **no sp_cerror** in
   either aplcobis mode. When `oError == 0` (A14) the result is `DebitResult(0, 0, null)` after
   everything was rolled back. Committed state: the debit write and the Phase 1 'P' movement are
   gone; only the 'X' movement survives. `numError` (122003 or `oError`) is computed and unused.
5. Lines 1716-1730 (`if @w_cod_errord != 0 ... commit ... return`) are unreachable: a non-zero
   `oError` always took exit B. Pinned as "a failed first commission never commits".
6. **Second, SWIFT commission** (1742-1836), only if `isnull(iValor2Swift, 0) > 0` and the trimmed
   `servicio` equals `"TRANSWIFT"`: same command shape with `iValorComision = iValor2Swift`,
   `iReferencia = request.iReferencia()` (not the literal), `iTipoafec = "16"`,
   `iValorTarifa = iValor2Swift`, the five other REF33 fields `null`, `iCadena = cadena`,
   `iFrmPagcob = frmPagcob`, same savepoint and `iSecuencial 0`. RULE-016.
7. Its failure (`returnCode != 0` or throw or `oError != 0`): `numError = oError != 0 ? oError :
   122003`, return `LblError(numError)` -> exit C of section 4 step 18 (full rollback; aplcobis `'S'`:
   `sp_cerror(iSpName, numError)` and `DebitResult(numError, 0, null)`; else
   `DebitResult(0, numError, null)`). No movement is written by this procedure for that failure.
   Lines 1844-1854 are unreachable: pinned as "a failed SWIFT commission never commits". RULE-017.
8. Happy path: `Continue(frmPagcob)`; the service runs the order-header step and commits. Both
   commission writes survive the commit.

### Test doubles

`FakeAseSession` implements `CommissionPort`: `commission(returnCode, oError)` scripts the first
call, `secondCommission(returnCode, oError)` the SWIFT call (recognised by `iTipoafec = "16"`),
`commissionThrows()` / `secondCommissionThrows()` raise `AsePortException`; every call records a
write `"sp_grb_comision"` (the procedure records its own movement) so the exit-B rollback is seen
to discard it. `service()` wires `new CommissionDebits(session, session, session)` behind a logging
`CommissionStep` that still records `"commissionStep"` and the `CommissionContext`.

## Phase 3: order-header update (B12, lines 1862-2090)

The stubbed `OrderHeaderStep` of section 3 becomes a real step. Everything else in this file stays.
Legacy oracle: lines 1862-2090 (the commented ROLPAGO block 2094-2124 is **not** migrated, RULE-020).
Business logic preserved as-is. Rules: RULE-014 (P0), RULE-018, RULE-010.

### New port, changed records

```java
public interface OrderHeaderRepository {            // bp_total_orden (1886-1898), db_sat_his..bp_total_orden_his (1908-1920)
    int markLiveInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError);
    int markHistoryInTransition(Integer ordenBanco, List<String> paymentForms, String servicio, int codError);
}
// each: update ... set te_estado_proceso = 'T', te_codigo_error = codError
//       where te_orden_banco = ordenBanco and te_frm_pagcob in (paymentForms) and te_servicio = servicio
//         and isnull(te_estado_proceso, 'I') = 'I'
// returns @@rowcount; an ASE failure is AsePortException (= @@error <> 0).

public record OrderHeaderContext(DebitRequest request, String servicio, String frmPagcobDeb, String actTotord,
        Integer codErrord, Integer priorRowCount) {}

public interface OrderHeaderStep { int update(OrderHeaderContext ctx); }   // 0 = continue, else numError for lbl_error
public final class OrderHeaderTransition implements OrderHeaderStep {
    public OrderHeaderTransition(OrderHeaderRepository headers) { ... }
}
```

`AseSession` also extends `OrderHeaderRepository`. `DebitFlowConfiguration` wires
`new OrderHeaderTransition(ase)`.

`OrderHeaderContext` carries the working values: `servicio` = `@i_servicio` after the SPI-return
lookup (section 4 step 14; NULL when that order was found nowhere), `frmPagcobDeb` =
`isnull(@i_frm_pagcob_deb, @i_frm_pagcob)` (line 262; NOT the TRANSQUICK-swapped commission form),
`actTotord` ('N' for an SPI return), `codErrord` (`@w_cod_errord`, always 0 on this path) and
`priorRowCount` = the value `@wRowdbBiz` holds when B12 starts. Today the service passes `null`
(declared NULL at line 140; the B7 reads at 824/840 and 890/912 are Phase 4 and stubbed; the
SPI-return lookup at 1358 only sets it when `actTotord` is already 'N', where it cannot matter).
Phase 4 will fill it.

### Behavior pinned

1. Channel test (1876-1880): `iCanal` equals `DIR`, `SFR`, `FR2`, `BTH` or `VEN` with Sybase `=`
   (trailing blanks ignored, a leading blank does not match; a NULL channel cannot arrive: the
   compact constructor made it `DIR`, RULE-037).
2. Direct channel: `iOpcion` (`=`, trailing blanks ignored) `'01'` -> `paymentForms =
   Collections.singletonList(frmPagcobDeb)` (may hold a single NULL, which matches nothing; the
   UPDATEs still run); `'02'` -> `List.of("CUE","EFE","CHL")`; `'03'` -> `List.of("CUE","EFE","CHE")`;
   anything else, NULL included -> **no UPDATE at all** and the row count stays `priorRowCount`.
3. Any other channel -> `List.of("COB","TRC","CTB","CPD","TPD")` whatever the option.
4. When an UPDATE runs: `rows = markLiveInTransition(iOrden, forms, servicio, codErrord)`; if
   `rows <= 0`: `rows = markHistoryInTransition(same arguments)`. The history table is never touched
   when the live update moved a row (RULE-018).
5. **Approved parity decision (plan gate):** the legacy never checks `@@error` on these UPDATEs. An
   `AsePortException` from either counts as 0 rows and the flow continues (live failure -> history is
   tried; history failure -> 0).
6. RULE-010 (2076-2090): if `rows` is non-null and `== 0`, and `servicio` is **not** one of
   `TRANSWIFT, IMPADUAN, PAGIESS, TRANSQUICK, TRANSBIMO, PAGOPRV` (Sybase `not in`: trailing blanks
   ignored, a leading blank is not in the list; a NULL `servicio` makes `not in` unknown -> false),
   and `actTotord` equals `'S'` -> return `122004`. Otherwise return 0. A `null` row count (stale NULL
   `@wRowdbBiz`) never raises 122004.
7. The service: non-zero from `update` -> `errorExit(request, n, true)` (exit C of section 4 step 18:
   full rollback; aplcobis 'S' -> `sp_cerror(iSpName, 122004)` and `DebitResult(122004, 0, null)`; else
   `DebitResult(0, 122004, null)`); zero -> `commit()`, `DebitResult(0, 0, null)`.
8. Exit A and exit B never reach the step (the order stays 'I': walkthrough 4 is re-runnable). No
   other write happens in B12 (the ROLPAGO `bp_orden` update is commented out in the legacy).

### Test doubles

`FakeAseSession` implements `OrderHeaderRepository` over two in-memory tables (`HeaderTable.LIVE`,
`HeaderTable.HISTORY`) of `HeaderRow(order, form, service, state, codError)`, scripted with
`headerRow(...)`, `historyHeaderRow(...)`, `noHeaderRows()` and `headerUpdateThrows(table)`. The WHERE
clause uses Sybase semantics. Row changes follow the transaction model (snapshot at `begin` and each
`savepoint`, restored by `rollback`/`rollbackToSavepoint`, kept by `commit`), so a 122004 rollback
restores every row. Each UPDATE is logged in `headerUpdates()` (`HeaderUpdate(table, ordenBanco,
paymentForms, servicio, codError, rowCount)`, `rowCount = -1` when it raised). The header UPDATEs are
**not** added to `calls()` or `committedWrites()`, which keep their Phase 1/2 meaning (procedure calls,
procedure writes). A session whose header tables were never scripted (the Phase 1/2 tests) answers 1
row to the live update, i.e. "the order header exists in state I". `service()` wires the real
`OrderHeaderTransition(this)` behind a wrapper that still logs `"orderHeaderStep"` and records the
context.

## Phase 4: notifications (B7, lines 746-1256)

The stubbed `NotificationStep` of section 3 becomes a real step, `CustomerNotifications`. Everything else
in this file stays, except where this section says "changes". Legacy oracle: lines 746-1256 (and 1358,
2134, 2150-2170 for what B7 leaves behind). Business logic preserved as-is; brief section 7 A11
(stray result set at 882 not emitted), A15 (notifier `@o_error` reaches the caller), A16 (lowest
`ct_cod_catalogo` wins) and A17 (`@wRowdbBiz` from the live read only) approved. Rules: RULE-034,
RULE-035, RULE-019, RULE-003, RULE-004, RULE-011, RULE-005, RULE-006, RULE-036, RULE-021, RULE-012.

### New ports and records (all in `com.nexti.debcred`)

```java
public interface NotificationCatalogReader {              // db_biz_admempresa..ba_tabla / ba_catalogo
    // 798-810: ct_cod_catalogo of EVERY row with tb_cod_tabla = ct_cod_tabla, tb_nom_tabla = 'ad_servicios_sms',
    //   ct_nom_catalogo like '%' + ltrim(rtrim(servicio)) + '%'   (no escaping: % and _ stay wildcards, SEC parity),
    //   right(ltrim(rtrim(ct_cod_catalogo)), 3) = canalSms, ct_est_catalogo = 'A'   (NO tb_est_tabla filter),
    //   ORDER BY ct_cod_catalogo ascending (server sort order). Empty list = no row.
    List<String> smsServiceCodes(String servicio, String canalSms);
    // 998-1010: ct_otro_campo_catalogo of the row with tb_nom_tabla = 'ad_notificacion_basica',
    //   ct_cod_catalogo = servicio (Sybase '=', the raw value, NOT trimmed), tb_est_tabla = 'A', ct_est_catalogo = 'A'.
    //   Empty = no row or a NULL value (both end as 'OTRO').
    Optional<String> notificationClass(String servicio);
    // 1166-1180: exists a row with tb_nom_tabla = 'ba_bloqueaNotificacionSAT',
    //   isnull(ct_nom_catalogo, '') = rtrim(servicio) + '-' + rtrim(servicioSms),
    //   isnull(ct_otro_campo_catalogo, '') = rtrim(spName), ct_est_catalogo = 'A'   (NO tb_est_tabla filter).
    //   The rtrim/concatenation is done in SQL with the raw values bound.
    boolean notificationBlocked(String servicio, String servicioSms, String spName);
}

public interface OrderDetailReader {
    // 826-838 db_biz_pagos..bp_orden x db_biz_pagos..bp_detalle: or_orden_banco = ordenBanco,
    //   or_orden_banco = dt_orden_banco, dt_secuencial = secuencial, or_ordenante = ordenante
    List<SwiftCreditDetail> liveSwiftCreditDetails(Integer ordenBanco, Integer secuencial, Integer ordenante);
    // 844-856 same over db_sat_his..bp_orden_his x db_sat_his..bp_detalle_his
    List<SwiftCreditDetail> historySwiftCreditDetails(Integer ordenBanco, Integer secuencial, Integer ordenante);
    // 892-910 db_biz_pagos..bp_detalle x ba_tabla x ba_catalogo: dt_orden_banco = ordenBanco,
    //   dt_referencia_grupo = substring(ct_nom_catalogo, 1, 9), tb_nom_tabla = 'ad_cuentas_bce',
    //   tb_est_tabla = 'A', tb_cod_tabla = ct_cod_tabla, ct_est_catalogo = 'A'
    List<InterbankCreditDetail> liveInterbankCreditDetails(Integer ordenBanco);
    // 916-932 same with db_sat_his..bp_detalle_his
    List<InterbankCreditDetail> historyInterbankCreditDetails(Integer ordenBanco);
    // 1024-1030 UNQUALIFIED bp_detalle -> <cobis-database>..bp_detalle (brief 7 A8): dt_orden_banco = ordenBanco
    List<BeneficiaryDetail> liveBeneficiaryDetails(Integer ordenBanco);
    // 1036-1044 db_sat_his..bp_detalle_his: dt_orden_banco = ordenBanco
    List<BeneficiaryDetail> historyBeneficiaryDetails(Integer ordenBanco);
}
// One element per row the SELECT returned, i.e. list size = @@rowcount. Raw column values; the step
// applies the substrings and the variable widths.
public record SwiftCreditDetail(String referenciaGrupo /*dt_referencia_grupo*/, String nomCuenta /*dt_nom_cuenta*/) {}
public record InterbankCreditDetail(String catalogName /*ct_nom_catalogo, whole*/, Integer tipoCta /*dt_tipo_cta*/,
        String numeroCuenta /*dt_numero_cuenta*/) {}
public record BeneficiaryDetail(String nombreBeneficiario, String referenciaGrupo) {}

public interface AccountOwnerReader {
    Optional<Integer> currentAccountClient(String ctaBanco);            // 1110-1114 cob_cuentas..cc_ctacte.cc_cliente
    Optional<Integer> savingsAccountClient(String ctaBanco);            // 1128-1132 cob_ahorros..ah_cuenta.ah_cliente
    Optional<VirtualAccountOwner> virtualAccountOwner(String ctaBanco); // 1146-1152 cob_virtuales..vi_cuenta
}
public record VirtualAccountOwner(Integer cliente /*vi_cliente*/, Integer prodBanc /*vi_prod_banc*/) {}

public interface BasicNotificationPort {                  // <cobis-database>..pa_sat_pnotificacion (1052-1082, A8)
    BasicNotificationResult notifyBasic(BasicNotificationCommand c);
}
public record BasicNotificationCommand(String iCanal, String iCtadebito, Integer iTipctadeb, String iServicio,
        Integer iOrdenBanco, String iDireccionTransf, Integer iSecuencial, BigDecimal iValor, String iNombrecred,
        BigDecimal iComision, String iCtacred, String iProdCre, String iEmpresa) {}
// oError: the @o_error OUTPUT after the call. The legacy passes its own @o_error, which is always 0 at that
// point (line 256), so the adapter binds 0 as the input value; a procedure that never assigns it answers 0.
public record BasicNotificationResult(int returnCode, Integer oError, String oMsg) {}

public interface EventNotificationPort {                  // cob_internet..sp_eventos (1210-1242)
    EventResult registerEvent(EventCommand c);
}
public record EventCommand(String iOperacion /*'I'*/, String iCanal /*@w_canal_sms*/, String iServicio /*@w_serv_sms*/,
        Integer iProducto, String iCuenta, String iValor, String iCtaDeb, String iProdDeb, String iCtaCre,
        String iProdCre, Integer iCliente, String iCosto, String iEmpresa, String iDescCanal) {}
public record EventResult(int returnCode) {}
```

**Changes:** `AseSession` also extends `NotificationCatalogReader, OrderDetailReader, AccountOwnerReader,
BasicNotificationPort, EventNotificationPort`. `JdbcAseSession` and `PgAseSession` implement them.

```java
// changes: two components added; the three-argument constructor keeps every Phase 1-3 test compiling
public record NotificationOutcome(boolean configured, int returnCode, BigDecimal valorDebito,
                                  Integer oError, Integer wRowdbBiz) {
    public NotificationOutcome(boolean configured, int returnCode, BigDecimal valorDebito) {
        this(configured, returnCode, valorDebito, 0, null);
    }
    public static NotificationOutcome notConfigured() { return new NotificationOutcome(false, 0, null, 0, null); }
}

public final class CustomerNotifications implements NotificationStep {
    public CustomerNotifications(NotificationCatalogReader catalog, OrderDetailReader details,
                                 AccountOwnerReader owners, BasicNotificationPort basic, EventNotificationPort events) { ... }
}
```

`oError` = the value `@o_error` holds after B7: 0, except on the 'B' path where it is the notifier's
output (may be NULL, A15). `wRowdbBiz` = the value `@wRowdbBiz` holds after B7: the `@@rowcount` of the
**live** TRANSWIFT or interbank read (840/912), NULL when neither read ran (A17). `valorDebito` =
the new working `@i_valor_debito` for TRANSCLI/TARJCRED/COMEXT (886), null otherwise ("no replacement").

`DebitFlowConfiguration` wires `new CustomerNotifications(ase, ase, ase, ase, ase)` in place of the stub.
`ProcedureSignatureCheck` also verifies, at startup, `PA_SAT_PNOTIFICACION` in `<cobis-database>` and
`SP_EVENTOS` in `cob_internet` (lists below, same prefix rule as `SP_GRB_COMISION`).

### The step, in legacy order (`notify(ctx)`)

Working values: `r = ctx.request()`, `servicio = r.iServicio()`, `trim(x)` = `ltrim(rtrim(x))` with blanks
only, where an all-blank result is NULL (ASE).

0. **Entry guard.** If `trim(servicio)` is NULL (a NULL or all-blank service), return `notConfigured()`
   without reading anything: line 746 `ltrim(rtrim(@i_servicio)) <> 'PAGOPRV'` is unknown there and the
   legacy never enters B7 (the Phase 1 service guard lets such a request reach the step).
1. **Channel (760-794, RULE-035).** `canalSms = convert(char(3), r.iCanal())`: the first three characters,
   right-padded with blanks. `canalSms` equal (trailing blanks ignored) to `DIR` or `SAT` -> `canalSms =
   "SAT"`, `descCanal = "SAT"`; `BNK` -> `"IBK"`, `"24OnLine"`; `VEN` -> `canalSms` unchanged,
   `"Ventanilla"`; anything else -> unchanged, `descCanal = null`. The tests compare `canalSms`
   ignoring trailing blanks.
2. **SMS service (798-810, RULE-035, A16).** `codes = catalog.smsServiceCodes(servicio, canalSms)`; if
   empty -> `notConfigured()`. Else take `codes.get(0)` (the lowest) and derive
   `servSms = trim(substring(code, 1, patindex('%-%', code) - 1))` truncated to varchar(10). A code with
   no `-` gives `substring(code, 1, -1)` = NULL in ASE; a code starting with `-` gives length 0 = NULL;
   a blank prefix trims to NULL. **A NULL `servSms` is `notConfigured()`** (line 814 false), even though a
   row matched and even if a higher code would have given a value (A16: the row is chosen first).
3. From here the outcome is `configured = true`; `returnCode = 0` unless a notifier is called.
4. **TRANSWIFT (820-874, RULE-019)**, `trim(servicio)` = `TRANSWIFT`: `live = details.liveSwiftCreditDetails(
   r.iOrden(), r.iSecuencial(), r.iEmpresa())`; `wRowdbBiz = live.size()`; if `live` is empty,
   `details.historySwiftCreditDetails(same)` (`wRowdbBiz` stays 0). From the row found (any, if
   several): `empInst = referenciaGrupo` (varchar(32)), `ctaCre = substring(nomCuenta, 1, 30)`; none found:
   both NULL. `valorComision = ctx.valorComision()` (the normalized `@i_valor_comision`).
   Any other service: `empInst = ctaCre = null`, `valorComision = null`.
5. **Interbank (878-976, RULE-003, RULE-004, RULE-011)**, `trim(servicio)` in `TRANSCLI, TARJCRED, COMEXT`:
   `valorComision = ctx.comision()` (normalized `@i_comision`); **new working debit value =
   `r.iValorOrdenado()`** (returned as `valorDebito`, used by the movement and by step 7); the line-882
   result set is NOT emitted (A11). `live = details.liveInterbankCreditDetails(r.iOrden())`; `wRowdbBiz =
   live.size()`; if empty, `details.historyInterbankCreditDetails(r.iOrden())`. From the row found:
   `empInst = substring(catalogName, 10, 32)` (NULL when the name has fewer than 10 characters),
   `tipCta = tipoCta`, `ctaCre = numeroCuenta` (varchar(30)); none found: all three stay NULL. `prodCre`:
   `tipCta` 0 -> NULL, 3 -> `CTE`, 4 -> `AHO`.
6. **Outside that branch (956-974, RULE-004/005):** `tipCta` 9 -> `CON`, 8 -> `ESP` (`tipCta` is only ever
   set by step 5, so this matters only for the interbank services). Any other value, NULL included:
   `prodCre = null`.
7. **Cost and value texts (980-986, RULE-006):** `costo = isnull(valorComision, 0) + isnull(r.iValor2Swift(), 0)`;
   `valorSms = convert(varchar(11), <working debit value>)` and `costoSms = convert(varchar(11), costo)`:
   ASE money-to-text, plain digits, `.`, exactly two decimals (a 4-decimal money is rounded to cents), no
   thousands separator, NULL stays NULL (`100 -> "100.00"`, `0.5 -> "0.50"`, `99999999.99` is the
   longest value that fits 11 characters; longer values are an open question, not pinned).
8. **Classification (994-1014, RULE-036):** `cls = catalog.notificationClass(servicio)`, the value
   truncated to varchar(4), default `OTRO`. Compared with Sybase `=` (trailing blanks ignored): `B`,
   `OTRO` (so `OTROS` -> `OTRO`), anything else -> no notifier at all.
9. **'B' (1020-1082):** `live = details.liveBeneficiaryDetails(r.iOrden())`; if empty, history. From the
   row found: `nombre` (varchar(64)), `refGrupo` (varchar(20)); none: both NULL. Then
   `basic.notifyBasic(new BasicNotificationCommand(r.iCanal() /*raw, NOT canalSms*/, r.iNumctaEmp(),
   r.iTipctaEmp(), r.iServicio(), r.iOrden(), refGrupo, r.iSecuencial(), r.iValorOrdenado(), nombre,
   ctx.valorComision(), ctaCre, prodCre, empInst))`; `returnCode = result.returnCode()`; **`oError =
   result.oError()`** (A15). This read does not touch `wRowdbBiz`.
10. **'OTRO' (1094-1242):** debtor product and client (RULE-005): type 3 -> `CTE`,
    `owners.currentAccountClient(numcta)`; 4 -> `AHO`, `savingsAccountClient`; 12 -> `VIR`, then with a
    `virtualAccountOwner` row: `cliente`, and `AHO` when `prodBanc == 13`; no row keeps `VIR` / NULL.
    Then (always, after the owner read) `blocked = catalog.notificationBlocked(servicio, servSms,
    r.iSpName())` (RULE-021). BCE exception: `canalSms` = `BCE` and `servicio` = `ROLPAGO` (Sybase `=`,
    not trimmed on the left) and `r.iNumctaEmp() == null`. If not blocked and not the BCE exception:
    `events.registerEvent(new EventCommand("I", canalSms, servSms, r.iTipctaEmp(), r.iNumctaEmp(),
    valorSms, r.iNumctaEmp(), prodDeb, ctaCre, prodCre, cliente, costoSms, empInst, descCanal))`;
    `returnCode = result.returnCode()`.
11. Return `new NotificationOutcome(true, returnCode, <new debit value or null>, oError /*0 unless 'B'*/, wRowdbBiz)`.

Several rows from a scalar-assignment read (e.g. the interbank join by order only): the legacy keeps an
undefined row. The step may keep any row, but the fields of one read come from **one and the same row**;
the rowcount is the number of rows. (Open question, not a rule.)

**Adapter (architecture review Phase 4 H1):** a notifier that raises an error (the COBIS `raiserror` +
return code convention) answers that status, not an exception: `JdbcAseSession` returns the return status
jTDS already read, else the ASE error number, else -1, so the step sees a non-zero code and exit A runs as
in the legacy. A lost transaction (1205, 08xxx) still throws `AseTransactionAbortedException`. The text
below is the contract as first written:
A port that throws propagates (section 1). The legacy would see a negative return status from a failing
notifier and take exit A; that path is an open question and is not pinned.

### Service changes (section 4)

- Step 10 (notification): when `configured`: `codErrord = returnCode` (1248), `valorDebito` replaced when
  non-null (886), **`oError` (working `@o_error`) = `outcome.oError()`**, **`wRowdbBiz = outcome.wRowdbBiz()`**.
  Not configured: nothing changes (`oError` 0, `wRowdbBiz` NULL).
- Step 14 (SPI return, 1352-1358): `wRowdbBiz = liveService present ? 1 : 0` (the live lookup's
  `@@rowcount`; the history lookup does not reassign it).
- Step 17 / Phase 3: `OrderHeaderContext.priorRowCount = wRowdbBiz`. The component keeps its Phase 3 name
  (it means "`@wRowdbBiz` as B12 finds it"); renaming it to `wRowdbBizBeforeB12` would touch nine Phase 3
  call sites for no behavioral gain.
- Success (2134): `DebitResult(0, oError == null ? 0 : oError, null)`.
- Exit C with `iAplcobis = 'S'` (2150-2158) does not assign `@o_error`: `DebitResult(numError, oError, null)`,
  i.e. the notifier's output (NULL included) after a 'B' notification, 0 otherwise. Exit C with 'N', exit A
  and exit B overwrite `@o_error` as before.

### Adapters

`JdbcAseSession`: `{? = call <cobis>..pa_sat_pnotificacion(15 placeholders)}` with parameters 2-14 in the
command order, 15 = `@o_error` (`setInt(15, 0)` and `registerOutParameter(15, INTEGER)`), 16 = `@o_msg`
(`registerOutParameter(16, VARCHAR)`); result `(getInt(1), (Integer) getObject(15), getString(16))`.
`{? = call cob_internet..sp_eventos(14 placeholders)}` in the command order; result `getInt(1)`. Reads as
the port comments above, one `PreparedStatement` each, bound in the order the WHERE clause names them;
the catalogue columns are the legacy ones (`tb_cod_tabla`, `tb_nom_tabla`, `tb_est_tabla`, `ct_cod_tabla`,
`ct_cod_catalogo`, `ct_nom_catalogo`, `ct_otro_campo_catalogo`, `ct_est_catalogo`). Integer columns are
read null-safely (`getObject` + `Number`, or `getInt` + `wasNull`). Integer arguments are bound with `setObject(i, value, Types.INTEGER)` (`Types.SMALLINT` for `@i_tipctadeb` / `@i_producto`), text with `setString`, money with `setBigDecimal`. **Exactly one `?` per procedure parameter** (indices 2..count+1): the Phase 1-3 helper `call(procedure, lastParameter, ...)` emits `lastParameter` placeholders while binding only 2..lastParameter, i.e. one unbound `?` (see the Phase 4 report); the Phase 4 tests pin the correct count.

```java
static final List<String> PA_SAT_PNOTIFICACION = List.of("@i_canal", "@i_ctadebito", "@i_tipctadeb", "@i_servicio",
        "@i_orden_banco", "@i_direccion_transf", "@i_secuencial", "@i_valor", "@i_nombrecred", "@i_comision",
        "@i_ctacred", "@i_prod_cre", "@i_empresa", "@o_error", "@o_msg");
static final List<String> SP_EVENTOS = List.of("@i_operacion", "@i_canal", "@i_servicio", "@i_producto", "@i_cuenta",
        "@i_valor", "@i_cta_deb", "@i_prod_deb", "@i_cta_cre", "@i_prod_cre", "@i_cliente", "@i_costo", "@i_empresa",
        "@i_desc_canal");
```

### Test doubles

`FakeAseSession` implements the five ports over small in-memory tables with the legacy WHERE clauses
(Sybase `=`, LIKE with `%`/`_`, NULL never equal): `catalogRow(table, code, name, otherField, state)` and
`catalogTableState(table, state)` (default `'A'`) with the shortcuts `smsService(code, name)`,
`notificationClass(service, value)`, `blockedNotification(key, spName)`, `bceInstitution(name)`; order
detail rows in `DetailTable.LIVE` / `HISTORY` via `swiftDetail(...)`, `interbankDetail(...)`,
`beneficiaryDetail(...)`, `detailRow(...)`; account masters via `currentAccountOwner`,
`savingsAccountOwner`, `virtualAccountOwner`; notifier answers via `basicNotification(returnCode, oError,
oMsg)` (default `(0, 0, null)`) and `event(returnCode)` (default 0). Both notifiers log their procedure
name in `calls()` and **always record a write** (`"pa_sat_pnotificacion"`, `"sp_eventos"`), so the
exit-A savepoint rollback and the exit-B/C full rollback are seen to discard them. Every read is logged,
in order, in `notificationLog()` (not in `calls()`, which keeps its Phase 1-3 meaning of procedure calls
plus transaction statements), with its arguments in the `...Queries()` lists; the two notifier calls also
appear in `notificationLog()` so read-then-call order can be asserted. The SMS read returns the matching
codes sorted with `String.compareTo` (a binary sort order).

`service()` wires the real `CustomerNotifications(this, this, this, this, this)` behind the logging
`NotificationStep` (still `"notificationStep"` + the context). **`notification(outcome)` still scripts the
step** (it then answers that outcome without running the real step): this keeps `NotificationStubTest`
unchanged. An unscripted session with an empty catalogue answers `notConfigured()`, exactly the Phase 1
stub, so no Phase 1-3 expectation moves.
