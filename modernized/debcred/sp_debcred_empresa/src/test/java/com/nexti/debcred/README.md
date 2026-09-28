# Characterization tests: `sp_debcred_empresa` (Phase 1 + Phase 2)

These tests pin what `legacy/debcred/sp_debcred_empresa.sp` **actually does** in blocks B0-B6,
B8, B9, B14 and B15 (lines 1-740, 1260-1492, 2130-2170) so the Java service can be proven
equivalent. The legacy is the oracle: the suspected defects the approver confirmed as-is
(`analysis/debcred/RULE_REVIEWS.json`) are asserted as behavior, not fixed. Phase 2 adds blocks
B10/B11 (lines 1496-1856): the two `sp_grb_comision` calls and exit B, including the approved quirk
of brief section 7 A14 (a commission failure that leaves `@w_cod_errord = 0` returns 0 / 0 after a
full rollback). Notifications (B7) are a stub here; the order-header update (B12) is a later phase
and only its stub is shown to be reached.

The production types the tests compile against are fixed in [`CONTRACT.md`](CONTRACT.md).

## Run

Maven is at `~/tools/apache-maven-3.9.9` (not on PATH). Everything resolves from the local cache:

```
cd modernized/debcred/sp_debcred_empresa
~/tools/apache-maven-3.9.9/bin/mvn -o test
```

One class: `mvn -o test -Dtest=Rule022LedgerDebitTest`. JUnit Jupiter and jqwik both run on the
JUnit Platform through Surefire; jqwik keeps its failure database under `target/.jqwik-database`
(`src/test/resources/junit-platform.properties`).

## Layout

| File | Legacy block | Rules |
|---|---|---|
| `Rule037Rule039DefaultsAndPassThroughTest` | B0 (4-96) | RULE-037, RULE-039 |
| `Rule027Rule002CommissionNormalizationTest` | B1 (168-248) | RULE-027, RULE-002 |
| `Rule002CommissionTruncationPropertyTest` (jqwik) | B1 (242-248) | RULE-002 |
| `Rule007Rule029Rule030AccountingConfigurationTest` | B2/B3 (254-394) | RULE-007 (P0), RULE-029, RULE-030, RULE-028 |
| `Rule038Rule031Rule032DebitReferenceTest` | B5 (422-614) | RULE-038, RULE-031, RULE-032 |
| `Rule009Rule008Rule033Rule026DebitRoutingTest` | B6 (414-740), 1308-1318 | RULE-009 (P0), RULE-008 (P0), RULE-033, RULE-026 |
| `Rule022LedgerDebitTest` | B8 (1264-1300) | RULE-022, RULE-009, RULE-012 |
| `Rule012Rule015Rule028Rule023ExitsTest` | B9/B14/B15 (1332-1492, 2130-2170) | RULE-012 (P0), RULE-015, RULE-028, RULE-023, RULE-024 |
| `NotificationStubTest` | B7 boundary (746, 814, 1248) | RULE-034/RULE-012 (stub contract) |
| `Rule013Rule001Rule025Rule039CommissionTest` | B10 (1496-1600) | RULE-013 (P0), RULE-001, RULE-025, RULE-039 |
| `Rule013ExitBTest` | exit B (1606-1732) | RULE-013 (P0), brief A14 |
| `Rule016Rule017SecondSwiftCommissionTest` | B11 (1742-1856) | RULE-016, RULE-017 |
| `GoldenCasesTest` + `resources/golden/phase1-cases.json` + `phase2-cases.json` | the four exits | table-driven, one dynamic test per case, one count over both files |
| `support/FakeAseSession` | the ASE connection | records call order and committed state |
| `support/Requests` | the 45 inputs | fake fixture values |

Every test method names the rule(s) it pins in its name (`rule007_...`) or its `@DisplayName`,
so `trace_rules.py` can find them.

## How the tests observe the legacy behavior

`FakeAseSession` is one object that is the `AseTransaction` and every port. It records:

- `calls()`: the exact order of `begin`, `savepoint:<name>`, `rollbackToSavepoint:<name>`,
  `rollback`, `commit` and each procedure/table access;
- `committedWrites()`: what survives after the service returns (writes after a savepoint are
  discarded by `rollbackToSavepoint`, all pending writes by `rollback`, kept by `commit`);
- the argument record each port received (`onlyDebitNote()`, `onlyMovement()`, ...). These are how
  the legacy's mutated parameters (`@s_term` blanked, `@i_comision` swapped, `@i_valor_debito`
  zeroed) are observed, because `DebitRequest` is immutable.
- Phase 2: `sp_grb_comision` is scripted with `commission(returnCode, oError)` for the first call
  and `secondCommission(returnCode, oError)` for the SWIFT call (`@i_tipoafec = '16'`),
  `commissionThrows()` / `secondCommissionThrows()` for `@@error`; `onlyCommissionCommand()` /
  `commissionCommands()` are what the port received; `lastMovement()` is the exit-B 'X' movement
  (the second `sp_grb_mov_y_frmpgo` call); `secondMovement(returnCode)` scripts its answer. The
  step under test is the real `CommissionDebits`, wired by `service()`.

`onlyX()` accessors throw when the call did not happen exactly once: a test cannot pass because the
thing it inspects is missing. `GoldenCasesTest` fails if its JSON fixture is missing or empty and
prints `equivalence cases executed: N`.

## The three exits, as pinned

| Exit | Trigger | Transaction | Result |
|---|---|---|---|
| A | debit procedure returned non-zero, or account type not in (3,4,9,12) -> 122001 | `rollbackToSavepoint("sp_debito_empresa")`, movement `X` + code, `commit` | `DebitResult(code, code, null)`; never `sp_cerror` |
| B | first `sp_grb_comision` failed (return value, `@@error` or `@o_error`) | full `rollback()`, then movement `X` / `COBRO DE COMISION` / `cod_error = @o_error` written in autocommit; **no commit** | `DebitResult(oError, oError, null)`, `(0, 0, null)` when `@o_error` stayed 0 (A14); never `sp_cerror` |
| C | 120000 (no accounting config), 122002 (movement write failed), SWIFT commission failed (its `@o_error` or 122003) | `rollback()` only if `begin()` happened | aplcobis `N`: `(0, code, null)`; aplcobis `S`: `sp_cerror(sp_name, code)`, `(code, 0, null)` |
| OK | everything returned 0 | `commit` | `(0, 0, null)` |

## Adding a case

1. Find the branch in the legacy file and note its line numbers and the `RULE-NNN` in
   `analysis/debcred/BUSINESS_RULES.md`.
2. Add a method to the class of that block (or a new `RuleNNN...Test`), named `ruleNNN_<what>`,
   with literal inputs via `Requests.currentAccount()...build()` and scripted port answers via
   `new FakeAseSession().tariff(...).accounting(...).debitNote(...)`.
3. Assert the result **and** the committed state / port arguments, not only the return code.
4. For a whole-flow case, add a row to `golden/phase1-cases.json` (the three Phase 1 exits) or
   `golden/phase2-cases.json` (commissions, exit B) instead; both files share one schema, the
   Phase 2 inputs (`comision`, `valorComision`, `valor2Swift`, `codSwift`, `frmPagcobSpi`,
   `commissionReturnCode`/`commissionOError`, `secondCommissionReturnCode`/`secondCommissionOError`)
   and `expected.movementStatuses` are optional.
5. If the behavior is not implemented yet, mark the test `@Disabled("pending RULE-NNN")`; never
   delete it.

## Secret handling

No credential or real account is copied from the legacy. Fixtures use account `0000012345`,
company `500`, order `123456`, terminal `TERM01`, user `usrtest`. A live ASE, if ever used, is
configured through environment variables only.

## Known limits (brief section 6)

No engine on this machine runs Sybase T-SQL, so the expected values are read from the source, not
recorded. The best verdict is PARTLY PROVEN until the bank's recorded outputs (section 7 A6) are
added to `golden/phase1-cases.json` / `golden/phase2-cases.json`.
