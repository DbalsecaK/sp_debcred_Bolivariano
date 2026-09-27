# Assessment: debcred

Date: 2026-09-27. Code: `legacy/debcred` → `sp-banco-bolivariano/sp_debcred_empresa.sp` (one file). Sources: an inventory by `find` + `wc` (no `scc`/`cloc` installed), the technology fingerprint, and three agents (structural map, technical debt, security), each of whose critical citations was re-read by hand. Nothing under `legacy/` was modified. No credentials were found, so there is no `SECRETS.local.md`. No instruction-shaped text was found in the source.

## Executive Summary

`sp_debcred_empresa` is one Sybase ASE / COBIS Transact-SQL stored procedure (2,173 physical lines, 871 code lines, 47 parameters) that debits a company's bank account for a payment order, charges commissions, records the movement and payment form, notifies the customer, and moves the order header to state `T`. It is small in size but dense in risk: it moves money across six databases through ten external procedures that are not in the tree, and its error handling has three different transaction outcomes depending on which step fails, one of which loses a successful debit and then writes an audit row outside any transaction (`:1622-1628`). Fifteen years of patches (REF2 to REF45, 2003 to 2018, at least 14 authors) left dead code, a customer id hard-wired into the ledger logic (`'1295'`, `:1276`), and a notification failure that reverses the debit (`:1248` → `:1338`). Recommendation: **Refactor, one piece at a time (`transform`)** onto PostgreSQL, keeping the procedure's signature as a façade for its COBIS callers, after the SME settles the transaction and notification semantics and after the ten called procedures and the DDL are obtained.

## System Inventory

| Item | Value |
|---|---|
| Files | 1 (`sp_debcred_empresa.sp`, UTF-8, CRLF, double-spaced: a blank line after every source line) |
| Physical lines | 2,173 |
| Blank / comment / code | 1,208 / 94 / **871** |
| Language | Transact-SQL, Sybase ASE dialect, COBIS framework conventions (`@s_*` session, `@i_*`/`@o_*`, `@w_*` work variables, `cobis..sp_cerror`, `REFnn:initials` change tags) |
| Parameters | 47 (5 session, 40 inputs, 2 outputs `@o_error`, `@o_reg_a_proc`) |
| Decision keywords | 79 `if`, 19 `else`, 1 `case`, 0 loops, 7 `goto`, 8 `return` |
| Transactions | 1 `begin tran`, 1 `save tran`, 4 `commit`, 3 `rollback`, 1 label `lbl_error` |
| SQL statements | 102 `select` (mostly variable assignments), 9 `update`, 0 `insert`, 0 `delete` (writes to money tables happen inside the called procedures) |
| External procedures called | 10, none in the tree |
| Tables referenced | 10 in 6 databases (`db_biz_pagos`, `db_sat_his`, `db_biz_admempresa`, `cob_ahorros`, `cob_cuentas`, `cob_virtuales`) plus `cob_internet` and `cobis` for procedures; no DDL in the tree |
| Tests | none |
| Build / deploy | none in the tree; installed with `isql` (PREFLIGHT answer 3) |
| Most complex region | B7 notifications (`:746-1256`, 510 lines, nested lookups with live/history fallback) and B12 order-state update (`:1876-2090`, four near-identical branches) |

Tool used: `grep`/`wc` by hand. Complexity ranked by decision keywords.

## Architecture at a Glance

The procedure is one program with fifteen functional blocks; the diagram is in `ARCHITECTURE.mmd`.

| Block | Lines | Role | Reads / writes | Calls |
|---|---|---|---|---|
| B1 Init and commission normalization | 168-272 | Moves `@i_comision` into `@i_valor_comision` when both are given; commission-per-transaction from `sp_con_comision`; `@w_cantidad` = money ÷ money into an `int` (`:244`) | — | `sp_con_comision` |
| B2 Concept resolution | 276-314 | For `SPI`, the accounting concept arrives in the session field `@s_term`, which is then blanked (`:278-286`); basic-account concept `'91'` for virtual product 13 | R `vi_cuenta` | — |
| B3 Accounting configuration | 318-396 | Transaction code `@w_trn` and causal via `sp_con_confcontable` (two variants); failure → 120000 before any transaction | — | `sp_con_confcontable` ×2 |
| B4 Transaction open | 402-408 | `begin tran`, savepoint `'sp_debito_empresa'` (name does not match the procedure) | — | — |
| B5 Reference string and special causal | 414-616 | `TRANSWIFT` / `IMPADUAN`+`CORPEI` special concepts; SPI devolution block mostly commented out (`:562-610`) | R `ba_tabla`, `ba_catalogo` | `sp_con_confcontable` |
| B6 Debit note by account type | 624-740 | Type 12 → `sp_vi_ndc_automatica` (canal forced to `'SAT'`, `@s_ofi=0`); types 3/4 → `sp_ndc_ahcc` | (inside callees) | `sp_vi_ndc_automatica`, `sp_ndc_ahcc` |
| B7 Notifications | 746-1256 | SMS / basic-account notification with beneficiary lookups; **overwrites the debit result with the notification result** (`:1248`) and mutates `@i_valor_debito` (`:886`); a stray result set at `:882` | R `ba_*`, `bp_orden`, `bp_detalle`, `*_his`, `cc_ctacte`, `ah_cuenta`, `vi_cuenta` | `pa_sat_pnotificacion`, `sp_eventos` |
| B8 Accounting account (type 9) | 1260-1322 | `sp_graba_tran_servicio`; company `'1295'` + `SPI` sends value 0 (`:1276-1278`); other types → 122001 | — | `sp_graba_tran_servicio` |
| B9 Debit outcome and movement | 1332-1492 | Debit failed → `rollback tran @w_savepoint`, record error movement, **commit**, return (partial commit path A) | R `bp_orden`, `bp_orden_his` | `sp_grb_mov_y_frmpgo` |
| B10 Commission debit | 1500-1732 | `sp_grb_comision`; on failure (REF44) **full `rollback tran`, then `sp_grb_mov_y_frmpgo` outside any transaction, return** (path B, `:1622-1700`); dead `commit` remnant `:1716-1730` | — | `sp_grb_comision`, `sp_grb_mov_y_frmpgo` |
| B11 Second SWIFT commission | 1742-1856 | `sp_grb_comision` with `@i_tipoafec='16'`; failure → `lbl_error` (inconsistent with B10) | — | `sp_grb_comision` |
| B12 Order-state update | 1876-2090 | Four branches by channel/`@i_opcion`, each `update bp_total_orden` with fallback to `bp_total_orden_his`; "no row" → 122004 unless the service is in an exempt list (`:2080`) | W `bp_total_orden`, `bp_total_orden_his` | — |
| B13 Commented ROLPAGO | 2098-2124 | Dead | — | — |
| B14 Success exit | 2130-2136 | `commit`, `return 0` | — | — |
| B15 `lbl_error` | 2140-2170 | Full rollback; COBIS mode returns the code and calls `sp_cerror`, otherwise returns 0 with `@o_error` set | — | `sp_cerror` |

**Three transaction outcomes coexist:** (A) debit failed → partial commit with an error movement (`:1486`); (B) commission failed → everything rolled back, error movement written with no transaction (`:1622-1628`); (C) any `lbl_error` → everything rolled back, nothing recorded unless `@i_aplcobis='S'`. The return-code convention also differs by path (`:1490`, `:1700`, `:2158`, `:2168`).

## Production Runtime Profile

No telemetry available (no APM, no batch logs, no execution counts). Volumes per service (`TRANSWIFT`, `SPI`, `TRANSCLI`, `ROLPAGO`…) and the frequency of each error code would sharpen the risk ranking; ask the bank for a month of `sp_cerror` / movement records for this procedure.

## Technical Debt (top 10 by remediation value)

| # | Finding | Where | Why it matters | Remediation |
|---|---|---|---|---|
| 1 | Commission-failure path does a **full** `rollback tran` (destroys the caller's transaction too under ASE nesting), then writes the error movement in autocommit, then returns without commit | `:1622-1700` | Loses a successful debit while recording a movement that says a commission failed; behavior differs from the debit-failure path (`:1338`, savepoint) and from B11 (`lbl_error`) | One transaction contract (savepoint-scoped or caller-owned); in the target, record failed movements through a compensation/outbox, never after a rollback |
| 2 | Notification result overwrites the debit result; `pa_sat_pnotificacion` writes into `@o_error` directly | `:1248`, `:1080` → `:1334-1342` | An SMS or event failure reverses a customer debit; the procedure's own comments do not say this is intended | Separate outcome variables; make notification a non-blocking event; **SME question 1** |
| 3 | Stray result sets in a write procedure | `:882` (live path for `TRANSCLI`/`TARJCRED`/`COMEXT`), `:1726` (unreachable) | Breaks any caller or façade that expects only output parameters | Remove; lint rule "no result sets from write procedures" |
| 4 | Dead and unreachable code; an output parameter never assigned | `:562-610`, `:2098-2124`, `:1716-1730`, `:1844-1854`; `@o_reg_a_proc` (`:84`), `@w_nombre_cuenta` (`:106`, passed as NULL eight times), `@w_msg` (`:160`) | Effort ported to behavior that does not exist; the interface advertises an output that is always NULL | Delete; drop or document `@o_reg_a_proc`; fix the stale comment at `:1314` |
| 5 | A customer id hard-wired into ledger logic | `:1276-1278` (company `'1295'` + `SPI` → debit value 0, amount moved to `@i_saldo`); `@i_tipo_chequera` receives the company id (`:1296`) | One customer's contract is invisible to any configuration-driven rewrite; parameter of `sp_graba_tran_servicio` abused as a side channel | Per-company configuration; **SME question 2** |
| 6 | Eight-way duplication of the order-state update with live/history fallback; repeated 30-argument calls | `:1884-2070`; `sp_grb_mov_y_frmpgo` ×2, `sp_grb_comision` ×2, `sp_con_confcontable` ×3; `'CHL'` vs `'CHE'` (`:1942`, `:1992`) | The "orders are archived to `db_sat_his` while still in flight" fact is architecture, not code; `@wRowdbBiz` at `:2076` tests only the last branch that ran | One archive-aware repository operation; forms table keyed by `@i_opcion`; **SME question 3** on CHL/CHE |
| 7 | Hard-coded configuration throughout control flow | service names (16 sites), channel codes (`:764-794`, `:1876-1880`), account types 3/4/9/12, concept codes `'91'`/`'512'`/`'16'`, error numbers 120000/122001-122004, catalog names, 8 cross-database names | Every literal is a hidden rule or environment binding; cross-database names alone block a port to PostgreSQL | Enumerations/config catalog as the first deliverable; explicit integration points for each database |
| 8 | Input parameters mutated as working variables | `@i_comision`/`@i_valor_comision` (`:188-196`), `@s_term` (`:282-284`), `@i_valor_debito` (`:886`, `:1278`), `@i_servicio` (`:1374`), `@i_frm_pagcob` (`:1502`), `@i_valor_tarifa` (`:1508`) | The value a later call receives depends on every branch before it; `@s_term` is an undocumented contract with the SPI caller | Freeze inputs, compute explicit locals; explicit `@i_concepto` parameter; **SME question 5** |
| 9 | Unchecked results and silent defaults | `sp_con_comision` failure → per-transaction commission 0 (`:224-248`); money ÷ money into `int` (`:244`); catalog lookups without `@@rowcount` (`:484`, `:798`, `:998`, `:1166`); `@o_error` from `sp_vi_ndc_automatica` immediately overwritten (`:660-666`) | Commission inputs to `sp_ndc_ahcc` can be 0/0 with no trace | Fail fast or log; SME: is "commission config missing" a hard stop? |
| 10 | God-procedure shape with 45 patch layers and inconsistent naming | 47 parameters (six REF33 pass-throughs after the outputs), five responsibilities in one body, `@w_`/`@v_`/camelCase mix, savepoint name mismatch, differing session handling between the three debit branches (`:632`, `:686`, `:1286`) | Nothing is unit-testable; each REF block is a rule without a requirement trace | Decompose along the five responsibilities; REF-to-requirement table with the SME before coding |

## Security Findings

No dynamic SQL, no hardcoded credentials, no SAST tool applicable to Sybase T-SQL here. Findings are integrity and authorization issues in a money-moving procedure.

| ID | CWE | Severity | Where | Scenario | Fix |
|---|---|---|---|---|---|
| SEC-001 | CWE-862 Missing authorization | High | `:4-12`, `:266`, `:632`, `:686` | Any principal with EXECUTE can debit any company account: `@s_user`/`@s_ssn` are never validated (`@s_ssn` defaulted to 0, `@s_ofi` forced to 0), and nothing checks that `@i_orden` belongs to `@i_empresa` or that `@i_empresa` owns `@i_numcta_emp` | Validate session and ownership before `begin tran`; restrict EXECUTE to the application role |
| SEC-002 | CWE-20 Improper input validation | High | `:52-58`, `:76`, `:642`, `:698` | Negative or zero `@i_valor_debito`, or one different from `@i_valor_ordenado`, is forwarded to the debit procedures; only commissions are sign-checked | Guard before `:402`: reject non-positive amounts and enforce debit = ordered (+ commission) |
| SEC-003 | CWE-20 / CWE-476 Null keys reach money movement | Medium | `:16-72`, `:1190` | Null `@i_numcta_emp`, `@i_orden`, `@i_fecha_proceso`, `@i_tipcta_emp` still open a transaction and rely on callees to reject; account type is validated only after the debit branch (`:1264-1318`) | Fail fast on every non-defaulted parameter; validate `@i_tipcta_emp in (3,4,9,12)` first |
| SEC-004 | CWE-362 / CWE-837 Duplicate processing | High | `:402-408`, `:1886-2068`, `:2076-2090` | Two concurrent calls (or a retried batch) for the same `@i_orden` both pass the debit because the order state is read and flipped `I→T` only at the end; for the exempt services at `:2080` a missing row is silently committed with the debit retained | Locking state transition (`update … where te_estado_proceso='I'`, `@@rowcount=1`) at the start of the transaction; remove or justify the exempt list |
| SEC-005 | CWE-460 / CWE-754 Improper cleanup on failure | High | `:1622-1628`, `:1394-1476` | Commission failure: full rollback, then an error movement written outside any transaction with its return code ignored; the order stays `I` and can be re-run (with SEC-004) | Savepoint-scoped rollback as at `:1338`; check the write's return |
| SEC-006 | CWE-1287 Row-count ignored on multi-table fallback | Medium | `:1886-2068` | If the order exists in both live and history tables, one is updated and the other stays `I`, leaving the databases out of sync; no `@@error` checks | One source of truth per order; check `@@error`; require exactly one row |
| SEC-007 | CWE-20 Unvalidated enumerations drive control flow | Medium | `:36`, `:66`, `:38`, `:1876-1882`, `:1374-1378` | Unknown `@i_canal` falls into the "other channels" branch; `@i_opcion` outside `01-03` on a DIR channel skips every state update yet commits the debit (`@wRowdbBiz` stale); `@i_tipo_afec='12'` on SPI replaces the service and disables the total-order check | Whitelist against the catalog at entry; reset `@wRowdbBiz` before `:1876`; `else goto lbl_error` |
| SEC-008 | CWE-697 Wildcard LIKE on a parameter | Low | `:806` | `like '%' + @i_servicio + '%'` matches several catalog rows for a short service name; the SMS service (and its cost) is attributed arbitrarily | Exact match on a dedicated column; escape wildcards |
| SEC-009 | CWE-778 Insufficient logging | Medium | `:1052`, `:1210`, `:1248`, `:2148-2170` | Non-COBIS mode errors leave no `sp_cerror` record; a notification failure is recorded as the debit error code | Separate notification outcome; always log with `@i_orden`, `@i_empresa`, `@s_user` |
| SEC-010 | CWE-840 Business-logic special case | Medium | `:1276-1278` | Any caller supplying `@i_empresa=1295` with `SPI` (no ownership check) gets a type-9 transaction with a zero debit | Configuration with authorization, or removal |
| SEC-011 | CWE-20 Session field repurposed as data | Low | `:278-286` | The caller controls the accounting concept through `@s_term`, and the terminal is blank in every downstream audit row | Explicit `@i_concepto` parameter |
| SEC-012 | CWE-682 Integer truncation of commission count | Low | `:242-248` | A commission that is not a multiple of the tariff is reported as fewer units than the money charged | Validate the remainder or compute server-side |
| SEC-013 | CWE-489 Leftover debug code | Low | `:882`, `:1726` | Unrequested result sets leak internal values and can break callers | Remove |

Credential inventory: none found.

## Documentation Gaps (top 5)

1. **Transaction semantics per failure.** Nothing states which of the three outcomes (partial commit, full rollback + autocommit row, full rollback) is the intended contract, nor whether callers open their own transaction. This is the first SME conversation.
2. **Notification coupling.** No comment says a failed SMS or event must reverse the debit (`:1248`); the REF9/REF11 tags only mark the insertion.
3. **The SPI contract through `@s_term`** (`:278-286`) and the SPI devolution path (`:1348-1380`, `@i_tipo_afec='12'`): who sets them, and what `@i_frm_pagcob_spi` means, is undocumented.
4. **Channel, service and `@i_opcion` vocabularies.** The literals (`DIR/SFR/FR2/BTH/VEN`, 16 service names, `01/02/03`, `CHL` vs `CHE`) have no catalog reference in the tree; the exempt-service list at `:2080` has five REF tags and no reason.
5. **The archive-while-in-flight pattern.** Every read and write falls back from `db_biz_pagos` to `db_sat_his`; when and by whom orders are archived is not described anywhere, and it decides the SEC-006 risk.

## Relative Scale

| Measure | Value |
|---|---|
| KSLOC (code lines) | 0.87 |
| Complexity index `2.94 × KSLOC^1.10` | **2.5** (on physical lines, 6.9) |
| Decision density | 91 decision keywords per 0.87 KSLOC |

The index is a relative size measure for ranking this system against others (BI-cobol's three programs: about 2.5 KSLOC). It is not a timeline, a cost or a schedule, and none is given here. The risk is out of proportion with the size: the dependency on ten unseen procedures and the money semantics dominate the effort, not the line count.

## Recommended Modernization Pattern

**Refactor → `transform`** (rewrite in another technology, one piece at a time, while COBIS keeps running).

Rationale. *Rehost* changes nothing and leaves SEC-001/004/005 in production. *Replatform/uplift* (ASE → SQL Server or a newer ASE) would carry the same T-SQL and the same three transaction outcomes, and no engine for either exists here, while PostgreSQL does (PREFLIGHT 3c). *Rearchitect/reimagine* is too large for one procedure whose callers and callees stay in COBIS. *Replace* has no product candidate: this is bank-specific ledger glue. A `transform` fits the constraints recorded in INTENT.md (behavior must match exactly, including known quirks) and PREFLIGHT (a slice with callers that must not break): rewrite the procedure's five responsibilities (accounting configuration, debit by account type, movement record, commission, order-state transition) as the new code over PostgreSQL, keep `dbo.sp_debcred_empresa` as a thin façade with the same 47-parameter signature and return conventions for its COBIS callers, and treat the ten called procedures as ports with contracts the SME signs, exactly as PRG016/PRG008 were treated in BI-cobol, but obtained **before** the build this time. The notification block (B7) is the natural first slice to isolate, because it is the one whose failure semantics must change or be consciously preserved. Prerequisites before `brief` approves any build: the ten procedures' source or signed contracts, the DDL of the ten tables, `ba_tabla` catalog rows, and recorded outputs from the bank's test ASE, since no engine here can run the original.

**Decision recorded 2026-09-27 (INTENT.md):** target is a **Java microservice**; business logic is **not** changed; the ten called procedures **stay in Sybase and are still invoked**. So the rewrite is a *technology* port, not a refactor of the rules: every quirk above (notification reverses debit, company 1295, three transaction outcomes, exempt services) is preserved unless a person rules otherwise in the brief, and SEC-001..013 are recorded as accepted parity risks pending rulings. The design risk moves to one place: reproducing `begin tran` / `save tran` / savepoint rollback across a JDBC connection to ASE while the Java service also calls the ten procedures on it. The "first slice = notifications" suggestion above is withdrawn; the first slice is whatever the brief names, most likely the debit-by-account-type path with its savepoint semantics, because that is where the transaction contract is proven or not.

Routes to: `/code-modernization:modernize-transform debcred sp_debcred_empresa java-spring` after `map`, `extract-rules`, `review` and an approved `brief`.
