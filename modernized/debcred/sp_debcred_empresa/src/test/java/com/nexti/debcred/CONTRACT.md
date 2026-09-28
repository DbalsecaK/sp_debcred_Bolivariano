# Production contract for `com.nexti.debcred` (Phase 1 + Phase 2)

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
