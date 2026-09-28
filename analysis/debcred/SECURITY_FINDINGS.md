# Security findings: debcred

Run of `/code-modernization:modernize-harden debcred`, 2026-09-28. Two scopes:

1. **Legacy** `legacy/debcred/sp_debcred_empresa.sp` (2173 lines, Sybase ASE T-SQL): the command's scan, a
   workflow of 29 agents (5 class finders, 24 refuters; no second judge was needed, since nothing stayed
   Critical or High).
2. **Modernized service** `modernized/debcred/sp_debcred_empresa/` (Java 21 / Spring Boot): one
   security-auditor, because the brief's Phase 5 exit criterion asks for "no open Critical or High finding" on
   the Java service that is not an accepted parity risk of section 7 A9.

No hardcoded credential was found in either scope; no `SECRETS.local.md` was written. No instruction-shaped
text was found in the source.

## Scorecard

| Scope | Critical | High | Medium | Low | Info | Top CWE |
|---|---|---|---|---|---|---|
| Legacy (confirmed after refutation) | 0 | 0 | 3 | 2 | 0 | CWE-639, CWE-20, CWE-665 |
| Java service | **1** | **2** | 3 | 4 | 1 | CWE-306, CWE-319, CWE-400 |

Legacy precision: 27 raw findings, 24 after de-duplication, **19 refuted as false positives (79%)**, 5 confirmed.

## Coverage gaps

All 5 finder classes returned and every finding was judged (legacy scan). The Java audit was one agent, not
the class-scoped workflow: its findings were verified by that agent reading the cited code, not by independent
refuters.

## 1. Legacy findings (confirmed)

The refuters lowered each to Medium or Low: exploiting them needs EXECUTE on the procedure or control of the
upstream caller. They overlap `ASSESSMENT.md` SEC-001..013, which section 7 A9 accepted as parity risks
(recorded, not fixed).

| ID | Sev | CWE | Location | Finding | Same as |
|---|---|---|---|---|---|
| HRD-001 | Medium | CWE-639 | `sp_debcred_empresa.sp:692` (also 628-662) | No check that `@i_numcta_emp` belongs to `@i_empresa` or to the ordering party of `@i_orden` before the debit | SEC-001 (A9) |
| HRD-002 | Medium | CWE-20 | `sp_debcred_empresa.sp:698` (642, 1294) | No sign or range check on `@i_valor_debito`, commissions and tariffs before the debit calls | SEC-002 (A9) |
| HRD-003 | Medium | CWE-665 | `sp_debcred_empresa.sp:2076` (1884-1982) | An `@i_opcion` outside 01-03 moves no header to 'T' and a NULL or stale `@wRowdbBiz` skips the 122004 guard: the order stays 'I' and can be debited again | SEC-004 (A9); preserved by A17 |
| HRD-004 | Low | CWE-117 | `sp_debcred_empresa.sp:2154` | Caller-supplied `@i_sp_name` written unchanged as `@t_from` in the COBIS error log | new, parity |
| HRD-005 | Low | CWE-390 | `sp_debcred_empresa.sp:2168` | The error path returns 0 (success) when `@i_aplcobis` is not 'S'; the code is only in `@o_error` | RULE-028 (confirmed parity) |

Refuted (19), among them: missing authentication (the procedure runs under an authenticated ASE login),
LIKE wildcard injection, card number in clear text, unmasked data to the SMS engine, company 1295 bypass,
debug result set at 882, the movement value overwritten by the ordered value (RULE-003, a business rule).
The full list with each refuter's reason is in the workflow journal of this run.

## 2. Java service findings

| ID | Sev | CWE | Location | Finding | Parity or defect |
|---|---|---|---|---|---|
| **JSEC-001** | **Critical** | CWE-306 | `api/DebitController.java:20-33`, `pom.xml` (no Spring Security), `application.yml` (no `server.address`) | `POST /debitos-empresa` has no authentication: anyone who reaches port 8080 debits any account under the service's ASE login | **Defect**: the legacy needed an authenticated ASE login with EXECUTE; the REST port removes that gate (A9 covers ownership, not authentication) |
| **JSEC-002** | **High** | CWE-319 | `application.yml:28`, `ase/AseSessionFactory.java:28-33` | No TLS to ASE (jTDS's `ssl=` targets SQL Server, likely not ASE) and no HTTPS: login and debit data travel in clear text | Defect |
| **JSEC-003** | **High** | CWE-400 | `CommissionBreakdown.java:51-57`, `AseText.java:79`, `DebitRequest.java:28` | An amount such as `1E999999999` (11 characters of JSON) makes a `BigDecimal` division build a number with about a billion digits: CPU and heap exhausted, the JVM can fall for every caller | Defect: an ASE `money` parameter cannot hold it |
| JSEC-004 | Medium | CWE-20 | `DebitRequest.java:21-35` | Declared widths (`char(1)`, `char(3)`, `varchar(10)`...) not applied; `7.9` accepted as `7` for integer fields: a request can take a branch the legacy (which truncates at binding) would not | Defect (breaks parity) |
| JSEC-005 | Medium | CWE-770 | `ase/AseSessionFactory.java`, `ase/JdbcAseSession.java` | Pool of 10, no query timeout, no body-size or rate limit | Defect |
| JSEC-006 | Medium | CWE-1104 | `pom.xml` | jTDS 1.3.1 is unmaintained since 2013 (no CVE claimed) | Defect |
| JSEC-007 | Low | CWE-117 | `CustomerNotifications.java:123`, `CommissionDebits.java:92-95`, `OrderHeaderTransition.java:63` | `iServicio` logged verbatim (CR/LF forge log lines) | Defect |
| JSEC-008 | Low | CWE-1188 | `UnconfiguredProfileGuard.java`, `localpg/*`, `pom.xml` | The release jar ships the `local-pg` profile and the PostgreSQL driver | Defect |
| JSEC-009 | Low | CWE-15 | `local-pg/run-local-pg.ps1` | The env file can override the 127.0.0.1 binding | Defect (local only) |
| JSEC-010 | Low | CWE-532 | `api/ApiErrorHandler.java:50` | At DEBUG the full cause chain (ASE raiserror text) is logged | Defect |
| JSEC-011 | Info | - | `target/` | Build output tracked in git (no secret found in it) | Hygiene |

Legacy risks carried unchanged into the service (accepted by section 7 A9, not blocking): SEC-001 (no ownership
check; session fields `sSsn`, `sUser`, `sTerm`, `sOfi` come from the client), SEC-002 (no amount checks),
SEC-004 (no idempotency; the section 7 A3-b idempotency store is not built yet), SEC-005 (exit B), and SEC-003,
006-010, 012. **JSEC-001 widens every one of them** from "an ASE login with EXECUTE" to "anyone on the network".

Checked clean: SQL injection (every value bound; the only concatenated identifiers are literals or the validated
`cobis` name), deserialization and mass assignment (immutable records, unknown JSON keys rejected), error
responses (correlation id only), no actuator, no credentials in tracked files, local-pg bound to 127.0.0.1.

### Dependency CVEs (Java service)

| Package | Installed | CVE | Fixed in | Status |
|---|---|---|---|---|
| net.sourceforge.jtds:jtds | 1.3.1 | none claimed; unmaintained | - (replace with SAP jConnect) | JSEC-006 |
| org.postgresql:postgresql | 42.7.13 | CVE-2024-1597, CVE-2025-49146 | 42.7.2 / 42.7.7 | not affected |
| ch.qos.logback | 1.5.38 | CVE-2024-12798, CVE-2024-12801, CVE-2025-11226 | 1.5.13 / 1.5.19 | not affected |
| org.yaml:snakeyaml | 2.6 | CVE-2022-1471 | 2.0 | not affected |
| Spring Boot 4.1.1, Spring 7.0.9, Tomcat 11.0.24, Jackson 3.1.5, HikariCP 7.0.2 | - | unknown to the auditor (newer than its knowledge) | - | **run `dependency-check-maven` or `osv-scanner` online before sign-off** |

The legacy has no dependency manifest (one T-SQL file): no CVE table applies.

## Remediation Log

| ID | Fix | Patch |
|---|---|---|
| Legacy | No Critical or High finding survived refutation: **no legacy patch was drafted** (`security_remediation.patch` not written). The Medium/Low legacy findings are A9 parity risks. | - |
| JSEC-001 | **Fixed 2026-09-28 (section 7 A21: mTLS).** `api/SecurityConfiguration`: HTTPS with `client-auth: need`; only a client certificate whose CN is in `DEBCRED_ALLOWED_CALLERS` is served, anything else 403; empty list refuses everyone; no key store, no start. `local-pg` stays plain HTTP on 127.0.0.1. Tests `MutualTlsWebTest` (5); `Canary: authorization changed to permitAll -> 4 tests failed`. | service code (no legacy patch) |
| JSEC-002 | TLS to ASE (confirm jTDS vs SAP jConnect 16 on the bank's ASE) and HTTPS or TLS at a gateway | not drafted: depends on the bank's ASE and the driver |
| JSEC-003 | **Fixed 2026-09-28 (section 7 A21).** `DebitRequest` refuses any of the 11 money inputs outside +/-922,337,203,685,477.5807 or with more than 38 decimals (HTTP 400), checking scale and precision before any arithmetic; `CommissionBreakdown` no longer echoes the value. Tests `Jsec003MoneyDomainTest` (9) and a web test; `Canary: decimals limit removed -> 1 test failed`. Checked over HTTP on local-pg: `1E999999999` -> 400. | service code (no legacy patch) |

JSEC-001 and JSEC-003 were fixed in the service code after the person's decision (section 7 A21), with tests and canaries: 514 tests, 0 failures. JSEC-002 (TLS to ASE) depends on the bank's ASE and the driver (section 7 A20).

## Patch Review

No patch was drafted, so none was reviewed.

## Phase 5 exit criterion

**Not met yet:** JSEC-001 (Critical) and JSEC-003 (High) are fixed; **JSEC-002 (High, TLS to ASE) is still open**, pending the bank's test ASE (section 7 A20).
