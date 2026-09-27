# Transformation notes: sp_debcred_empresa, Phase 1 (debit core and the transaction contract)

Date: 2026-09-27. Legacy: `legacy/debcred/sp_debcred_empresa.sp` (Sybase ASE / COBIS T-SQL, 2,173 lines). Target: `modernized/debcred/sp_debcred_empresa/` (Java 21, Spring Boot 4.1.1). Brief: `analysis/debcred/MODERNIZATION_BRIEF.md`, Phase 1, approved 2026-09-27 (David Balseca, "Phase 1 only").

**Equivalence is spec-based; the legacy was not executable here.** No Sybase ASE exists on this machine (PREFLIGHT 3b) and the bank's test ASE is not reachable (brief §7 A4: "no; se usan mocks; techo PARTLY PROVEN aceptado"). Every expected value in the tests was derived by hand from the cited legacy lines and from `BUSINESS_RULES.md`; the ten called procedures are in-memory doubles whose contracts are the **assumed** ones in `DATA_OBJECTS.md` (§7 A5). The best verdict `modernize-verify` can give is PARTLY PROVEN.

## Scope

Blocks B0-B6, B8, B9, B14 and B15 of the map (`analysis/debcred/topology.json`): the signature, commission normalization, concept and accounting configuration, `begin tran` + savepoint, the debit by account type (3/4 via `sp_ndc_ahcc`, 12 via `sp_vi_ndc_automatica`, 9 via `sp_graba_tran_servicio`), the movement record, and the three exits. First slice, as the brief names it: account type 3, happy path and debit failure (exit A).

Out of scope, present as steps that later phases fill in: notifications B7 (Phase 4, stub "not configured"), commissions B10-B11 (Phase 2, stub returns 0), order-header update B12 (Phase 3, stub returns 0).

## Mapping (legacy lines -> target)

| Behavior | Legacy `sp_debcred_empresa.sp` | Target | Rules |
|---|---|---|---|
| 45 parameters, defaults `'N'`, `'DIR'`, `'10'`, 0, 0 | 4-96 | `DebitRequest` (record, compact constructor) | RULE-037, RULE-039 |
| Batch flag from `@i_aplcobis` | 176-178 | `DebitCompanyAccountService.batchFlag` | RULE-026 |
| Bundled commission moved to separate commission | 188-196 | `CommissionBreakdown.of` | RULE-027 |
| Per-transaction tariff, truncated count, unit = tariff | 204-248 | `CommissionBreakdown.of` | RULE-002 |
| `convert(char(2), @i_mon_debito)`, `@w_act_totord`, `@i_frm_pagcob_deb`, `@s_ssn` defaults | 254-266 | `debit` lines "B1" | RULE-037 |
| SPI concept from `@s_term`, terminal blanked; `'91'` for basic virtual account | 278-312 | `debit` lines "B2", `VirtualAccountReader` | RULE-029 |
| Accounting configuration, TRANSQUICK + SPI form, 120000 before `begin tran` | 320-394 | `accountingQuery`, `errorExit(..., false)` | RULE-007, RULE-030 |
| `begin tran`, `save tran 'sp_debito_empresa'` | 402-408 | `AseTransaction.begin/savepoint`, `JdbcAseSession` | RULE-012 |
| Reference string, `'COD:'+swift`, NULL concatenation | 414-450 | `postDebitNote` | RULE-038 |
| IMPADUAN + CORPEI: NDCORPEI concept, causal `'512'`, 120000 inside the transaction | 456-550 | `postDebitNote`, `CatalogReader` | RULE-031 |
| SPI return reference | 558-614 | `postDebitNote`, `isSpiReturn` | RULE-032 |
| Virtual account: `sp_vi_ndc_automatica`, channel `'SAT'`, office 0, verify `'S'`, batch | 624-666 | `postDebitNote` (type 12), `VirtualDebitNotePort` | RULE-009, RULE-033 |
| Current/savings: `sp_ndc_ahcc` with `@i_nchq`, `@i_solca`, `@i_alterno_dos` | 676-738 | `postDebitNote` (types 3/4), `DebitNotePort` | RULE-009 |
| Notification hook: only on `@w_return = 0` and not PAGOPRV; outcome overwrites `@w_cod_errord` | 746, 1248 (884-886) | `postDebitNote` -> `NotificationStep` | RULE-012, RULE-034, RULE-003 |
| Accounting account: `sp_graba_tran_servicio`; company `'1295'` + SPI books balance, value 0 | 1260-1300 | `postLedgerDebit`, `LedgerDebitPort` | RULE-022 |
| Unsupported type: 122001, no debit, movement still `'X'` | 1308-1322 | `post` (default branch) | RULE-008 |
| `@w_sts_proc` `'P'`/`'X'`, `rollback tran @w_savepoint` | 1332-1342 | `debit` lines "B9" | RULE-012 |
| SPI return: service from `bp_orden` then `bp_orden_his`, `@w_act_totord = 'N'` | 1348-1380 | `OrderReader`, `isSpiReturn` | RULE-023, RULE-024 |
| `sp_grb_mov_y_frmpgo`, 122002 on failure or `@@error` | 1394-1476 | `movementCommand`, `MovementPort`, `errorExit(..., true)` | RULE-015 |
| Exit A: commit, return the debit code in both channels | 1480-1492 | `debit` (`if (posted.codErrord() != 0)`) | RULE-012, RULE-015 |
| Commission and order-header steps, then `commit tran`, `return 0` | 1500-2136 | `CommissionStep`, `OrderHeaderStep` (stubs), `debit` tail | RULE-013/014 (later phases) |
| `lbl_error`: rollback if open, `sp_cerror` and return code when `'S'`, else 0 + `@o_error` | 2140-2170 | `errorExit`, `ErrorReportingPort` | RULE-028 |
| One connection, literal transaction statements, ten procedures, three table reads | whole file | `ase/JdbcAseSession` | brief §7 A1 (a), A8 |
| REST face, return code + `@o_error` as the reply | (new) | `api/DebitController`, `api/ApiErrorHandler` | brief §7 A2, A10 |

## Deliberate deviations (all approved in the brief, none a business-rule change)

| # | Deviation | Rationale |
|---|---|---|
| D1-1 | The procedure becomes a REST API (`POST /debitos-empresa`); no T-SQL façade. Callers are re-pointed. | Brief §7 A2 (David Balseca, 2026-09-27). |
| D1-2 | The stray result set at line 882 is not emitted; `@o_reg_a_proc` is always null in the reply. | Brief §7 A11: accepted difference, to be listed in `KNOWN_DIFFERENCES.md`. |
| D1-3 | Legacy parameter defaults are applied when a JSON field is null, since JSON cannot tell "omitted" from "null"; unknown JSON keys are refused (400). | RULE-037 reading approved in the brief; architecture review H4. |
| D1-4 | Infrastructure failures (ASE unreachable, a JDBC failure the legacy did not map) are HTTP 503/502 problem details with a correlation id; business outcomes, error codes included, stay HTTP 200 with `returnCode` and `oError`. | Brief §7 A10; architecture review H2/H3. The legacy had no equivalent: an unmapped `@@error` simply propagated to the caller. |
| D1-5 | `convert(varchar, NULL)` yields NULL, never the text `"null"`. | Architecture review M3: a Java idiom, not a legacy behavior. CONTRACT.md updated in the same commit. |
| D1-6 | An overflow of the commission count (`int`) is an `AsePortException` instead of a silent wrap. | Architecture review L3: the legacy `int` assignment raises an arithmetic overflow (`@@error`). |
| D1-7 | The connection is kept in autocommit/unchained mode and the transaction statements are issued literally (`begin tran`, `save tran`, `rollback tran name`, `commit tran`). | Architecture review H1: with autocommit off, jTDS switches the session to chained mode, the literal `begin tran` nests and `commit tran` never commits. Unchained mode also allows Phase 2's exit B (a write after a full rollback). **First thing the bank's test ASE must confirm.** |

Preserved as-is (confirmed by the approver, `RULE_REVIEWS.json`): a configured notification's failure overwrites the debit result and reverses the debit (1248); company 1295 on SPI; the terminal as SPI concept carrier and blanked terminal in every downstream record; the truncated commission count and the unit commission forced to the tariff even when the lookup failed; the error text of 122001 naming only types 3, 4 and 9; `@o_error` left at 0 on `lbl_error` with `@i_aplcobis = 'S'`.

## Not migrated

| What | Legacy lines | Why |
|---|---|---|
| Commented ROLPAGO update (B13) | 2098-2124 | Dead code (inside `/* */`). |
| Commit/return remnants after REF44 | 1716-1730, 1844-1854 | Unreachable: 1606 and 1824 already return or `goto` on the same condition (Phase 2 will pin this with a test). |
| `@w_desc_concepto`, `@w_msg`, `@w_nombre_cuenta` | 136, 160, 106 | Declared and never read, or never assigned; `@w_nombre_cuenta` is passed as NULL and the port receives null. |
| `@o_reg_a_proc` assignment | 84 | Never assigned by the legacy; the reply carries null (D1-2). |
| Debug `select` statements | 882, 1726 | 882 is an approved difference (D1-2); 1726 is unreachable. |

## Proof

- Tests: **115 executed, 0 failures, 0 skipped** (`mvn -o test` from a clean `target/`): 109 characterization tests (JUnit 5 + 2 jqwik properties + 10 golden whole-flow cases) and 6 REST-edge tests. Every characterization test names the rule(s) it pins.
- `equivalence cases executed: 10 of 10` (`GoldenCasesTest`, `src/test/resources/golden/phase1-cases.json`; the test fails, never skips, when the fixture is missing).
- **Canary: `rollbackToSavepoint` replaced by a full rollback + new transaction -> 14 tests failed; `RoundingMode.DOWN` -> `HALF_UP` in the commission count -> 3 tests failed** (the jqwik property included). Result files kept in `analysis/debcred/equivalence/canary/sp_debcred_empresa/{savepoint,truncation}/`. Code restored and the clean run repeated.
- Dual execution: **not possible** (no ASE). No `analysis/debcred/equivalence/cases.json` was written: a comparison file with no legacy side would be a claim, not evidence. When the bank's test ASE is available, `modernize-verify` runs the original procedure and the service on the same inputs.
- Test doubles: `FakeAseSession` records the exact sequence of transaction and procedure calls and models committed state (writes after a savepoint discarded by `rollbackToSavepoint`, all by `rollback`, kept by `commit`, autocommit outside a transaction). Exit A is asserted on that committed state, not on method calls.

## Architecture review (architecture-critic, 2026-09-27)

Applied (HIGH and the cheap MEDIUM/LOW):

| # | Finding | Change |
|---|---|---|
| H1 | `setAutoCommit(false)` + literal `commit tran` never commits on jTDS (chained mode) | Autocommit on, literal statements kept; `inTransaction` tracked; documented as the first thing to confirm on a real ASE (D1-7) |
| H2 | A port exception mid-transaction closed the connection with the transaction open, silently | `JdbcAseSession.close()` rolls back an open transaction, logs the last statement at WARN, never throws; `ApiErrorHandler` logs SQLState and vendor code with a correlation id |
| H3 | Opaque 500 for every infrastructure failure | `ApiErrorHandler`: 503 `AseUnavailableException`, 502 `AsePortException`, 500 otherwise, RFC 9457 problem details, no SQL text |
| H4 | Unknown JSON keys dropped, then defaults applied | `fail-on-unknown-properties: true`; `DebitControllerWebTest` (binding of money scale and date-time, 400 on an unknown key, 200 for business errors, 503/502 for infrastructure) |
| M1 | Output parameters read with result sets pending | `drain()` after every `execute()` |
| M3 | `String.valueOf(null)` -> `"null"` | `AseText.toVarchar`/`toChar` return null for null (D1-5) |
| M5 | 14-argument constructor at the call site | `AseSession` = transaction + ten ports + `AutoCloseable`; 4-argument constructor; the 14-argument one kept for the tests' `FakeAseSession` |
| L1, L3, L4, L6, L7 | Dead parameter; `intValue` wrap; guard message; unused PostgreSQL/H2 deps; Unicode trims | Removed; `intValueExact` (D1-6); message names `SPRING_PROFILES_ACTIVE=ase`; deps removed until a phase uses them; blank-only `ltrim`/`rtrim` |

Not applied, listed for later:

| # | Finding | When |
|---|---|---|
| M2 | Positional `{? = call ...}` binding assumes the procedures' parameter order of `DATA_OBJECTS.md`. Bind by name (jTDS supports it) or check `DatabaseMetaData.getProcedureColumns` at startup. | As soon as the bank's ASE or the procedures' source is available (§7 A4/A5). |
| M4 | Pool sizing, timeouts, validation query, actuator health/metrics for the ASE datasource. | Phase 5 (deployment configuration). |
| M6 | No test of `JdbcAseSession` SQL text and parameter indexes (a Mockito `Connection` test). | Phase 2, together with the exit-B adapter work. |
| L2 | `cobisDatabase` validated at first use, not at startup. | Phase 5. |
| L5 | (done: the controller is no longer profiled) | — |

## Follow-ups for the next phases

1. **Phase 2 (commissions, B10-B11):** implement `CommissionStep` with `sp_grb_comision` and exit B exactly as the legacy: full `rollback tran`, then `sp_grb_mov_y_frmpgo` outside any transaction, return without commit (1606-1700). `FakeAseSession` already models autocommit writes outside a transaction. Pin that 1716-1730 and 1844-1854 are unreachable.
2. **Phase 3 (order header, B12):** `OrderHeaderStep` over `bp_total_orden` with the history fallback; note `@wRowdbBiz` is stale when `@i_opcion` is outside `01-03` (RULE-014 quirk, preserved).
3. **Phase 4 (notifications, B7):** replace the stub; `NotificationOutcome.configured = true` must overwrite the debit code (1248) and, for TRANSCLI/TARJCRED/COMEXT, the debit value (884-886).
4. **Before any real ASE run:** confirm D1-7 (transaction mode), M2 (parameter order), and whether the COBIS callers open their own transaction (brief §7 A1, unanswered).
5. `KNOWN_DIFFERENCES.md` (Phase 5): D1-2 and D1-3.

## Side by side: exit A (debit failed), legacy 1332-1342 and 1480-1492 vs the service

The block below is generated from the sources (`diff -y --width=160`); the legacy range holds no credential.

```
   select @w_sts_proc = 'P'						      |	        if (posted.codErrord() != 0) {                                       
   if @w_cod_errord != 0						      |	            tx.rollbackToSavepoint(SAVEPOINT);
   begin								      |	            stsProc = "X";
      rollback tran @w_savepoint					      |	        }
      select @w_sts_proc = 'X'						      |	-
   end									      |	        if (posted.codErrord() != 0) {                                       
   if @w_cod_errord != 0						      |	            tx.commit();
   begin								      |	            return new DebitResult(posted.codErrord(), posted.codErrord(), nu
      commit tran							      |	        }
      select @o_error = @w_cod_errord					      <
      return @o_error							      <
   end									      <
```
